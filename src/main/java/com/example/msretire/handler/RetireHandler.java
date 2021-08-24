package com.example.msretire.handler;

import com.example.msretire.models.dto.in.CreateRetireWithCardDTO;
import com.example.msretire.models.entities.*;
import com.example.msretire.services.BillService;
import com.example.msretire.services.DebitService;
import com.example.msretire.services.IRetireService;
import com.example.msretire.services.TransactionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.*;
import java.util.stream.Collectors;

import static org.springframework.http.MediaType.APPLICATION_JSON;

@Component
@Slf4j(topic = "RETIRE_HANDLER")
public class RetireHandler {
    private final IRetireService retireService;
    private final BillService billService;
    private final TransactionService transactionService;
    private final DebitService debitService;

    @Autowired
    public RetireHandler(IRetireService retireService, BillService billService, TransactionService transactionService, DebitService debitService) {
        this.retireService = retireService;
        this.billService = billService;
        this.transactionService = transactionService;
        this.debitService = debitService;
    }

    public Mono<ServerResponse> findAll(ServerRequest request){
        return ServerResponse.ok().contentType(MediaType.APPLICATION_JSON)
                .body(retireService.findAll(), Retire.class);
    }

    public Mono<ServerResponse> findById(ServerRequest request){
        String id = request.pathVariable("id");
        return errorHandler(
                retireService.findById(id).flatMap(p -> ServerResponse.ok()
                                .contentType(MediaType.APPLICATION_JSON)
                                .bodyValue(p))
                        .switchIfEmpty(ServerResponse.notFound().build())
        );
    }

    public Mono<ServerResponse> findByAccountNumber(ServerRequest request){
        String accountNumber = request.pathVariable("accountNumber");
        log.info("ACCOUNT_NUMBER_WEBCLIENT {}", accountNumber);
        return billService.findByAccountNumber(accountNumber).flatMap(p -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(p))
                .switchIfEmpty(Mono.error(new RuntimeException("THE ACCOUNT NUMBER DOES NOT EXIST")));
    }

    public Mono<ServerResponse> findByCardNumber(ServerRequest request) {
        String cardNumber = request.pathVariable("cardNumber");
        return debitService.findByCardNumber(cardNumber).flatMap(p -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(p))
                .switchIfEmpty(Mono.error(new RuntimeException("Debit card not found")));
    }
    public Mono<ServerResponse> updateBill(ServerRequest request){
        Mono<Bill> bill = request.bodyToMono(Bill.class);
        return bill.flatMap(billUpdate -> {
                Mono<Bill> billUpdated = billService.updateBill(billUpdate);
                return billUpdated;
                })
                .flatMap(billUpdate -> ServerResponse.created(URI.create("/bill/".concat(billUpdate.getAccountNumber())))
                        .contentType(APPLICATION_JSON)
                        .bodyValue(billUpdate))
                .onErrorResume(e -> Mono.error(new RuntimeException("Error update bill")));
    }

    public Mono<ServerResponse> createTransaction(ServerRequest request){
        Mono<Transaction> transaction = request.bodyToMono(Transaction.class);
        return transaction.flatMap(transactionCreate -> {
            Mono<Transaction> transactionCreated = transactionService.createTransaction(transactionCreate);
            log.info("TRANSACTION_RETIRE {}", transactionCreated);
            return transactionCreated;
        }).flatMap(transactionResponse -> ServerResponse.created(URI.create("/transaction/".concat(transactionResponse.getBill().getAccountNumber())))
                        .contentType(APPLICATION_JSON)
                        .bodyValue(transactionResponse))
                .onErrorResume(e -> Mono.error(new RuntimeException("Error create transaction")));
    }
    public Mono<ServerResponse> createRetireWithCardNumber(ServerRequest request){
        Mono<CreateRetireWithCardDTO> createRetireWithCardDTO = request.bodyToMono(CreateRetireWithCardDTO.class);
        Mono<Transaction> transactionMono = Mono.just(new Transaction());
        return Mono.zip(createRetireWithCardDTO, transactionMono)
                .zipWhen(data -> debitService.findByCardNumber(data.getT1().getCardNumber()))
                .zipWhen(result -> {
                    Transaction transaction = result.getT1().getT2();
                    transaction.setTransactionType("RETIRE");
                    transaction.setTransactionAmount(result.getT1().getT1().getAmount());
                    transaction.setDescription(result.getT1().getT1().getDescription());

                    if (result.getT1().getT1().getAmount() > result.getT2().getPrincipal().getBill().getBalance()){
                    List<Acquisition> acquisitions = result.getT2().getAssociations();
                    Acquisition acquisition = acquisitions.stream().filter(acq-> acq.getBill().getBalance() > result.getT1().getT1().getAmount()).findFirst().orElseThrow(() -> new RuntimeException("The retire amount exceeds the available balance in yours accounts"));
                    Bill bill = acquisition.getBill();
                    bill.setBalance(bill.getBalance() - result.getT1().getT1().getAmount());
                    transaction.setBill(bill);
                    }else {
                        result.getT2().getPrincipal().getBill().setBalance(
                                result.getT2().getPrincipal().getBill().getBalance()
                                        - result.getT1().getT1().getAmount());
                        transaction.setBill(result.getT2().getPrincipal().getBill());
                    }
                    return transactionService.createTransaction(transaction);
                })
                .zipWhen(updateDebit -> {
                    //update list
                    List<Acquisition> acquisitions = updateDebit.getT1().getT2().getAssociations().stream()
                            .peek(rx -> {
                        if (Objects.equals(rx.getBill().getAccountNumber(), updateDebit.getT2().getBill().getAccountNumber())){
                            rx.setBill(updateDebit.getT2().getBill());
                        }
                    }).collect(Collectors.toList());
                    //validate is principal
                    Acquisition currentAcq = acquisitions.stream()
                            .filter(acquisition -> Objects.equals(acquisition.getBill().getAccountNumber(), updateDebit.getT2().getBill().getAccountNumber()))
                            .findFirst().orElseThrow(() -> new RuntimeException("The account does not exist in this card"));
                    Boolean isPrincipal = updateDebit.getT1().getT2().getPrincipal().getIban().equals(currentAcq.getIban());
                    if (Boolean.TRUE.equals(isPrincipal)){
                        updateDebit.getT1().getT2().getPrincipal().setBill(updateDebit.getT2().getBill());
                    }
                    Debit debit = new Debit();
                    debit.setAssociations(acquisitions);
                    debit.setPrincipal(updateDebit.getT1().getT2().getPrincipal());
                    debit.setCardNumber(updateDebit.getT1().getT2().getCardNumber());
                    return debitService.updateDebit(debit);})
                .flatMap(response -> {
                    CreateRetireWithCardDTO retireWithCardDTO = response.getT1().getT1().getT1().getT1();
                    Retire retire = new Retire();
                    retire.setAmount(retireWithCardDTO.getAmount());
                    retire.setDescription(retireWithCardDTO.getDescription());
                    retire.setBill(response.getT1().getT2().getBill());
                    return retireService.create(retire);
                })
                .flatMap(retireCreate ->
                        ServerResponse.created(URI.create("/retire/".concat(retireCreate.getId())))
                                .contentType(APPLICATION_JSON)
                                .bodyValue(retireCreate))
                .onErrorResume(e -> Mono.error(new RuntimeException(e.getMessage())));
    }
    public Mono<ServerResponse> createRetireV2(ServerRequest request){
        Mono<Retire> retireRequest = request.bodyToMono(Retire.class);
        return retireRequest
                .zipWhen(retire -> billService.findByAccountNumber(retire.getBill().getAccountNumber()))
                .zipWhen(result -> {
                if (result.getT1().getAmount() > result.getT2().getBalance()){
                    return Mono.error(new RuntimeException("The retire amount exceeds the available balance"));
                }
                Transaction transaction = new Transaction();
                result.getT2().setBalance(result.getT2().getBalance() - result.getT1().getAmount());
                transaction.setTransactionType("RETIRE");
                transaction.setTransactionAmount(result.getT1().getAmount());
                transaction.setBill(result.getT2());
                transaction.setDescription(result.getT1().getDescription());
                return transactionService.createTransaction(transaction);
        })
                .flatMap(response -> {
                    response.getT1().getT1().setBill(response.getT2().getBill());
                    return retireService.create(response.getT1().getT1());
                })
                .flatMap(retireCreate ->
                        ServerResponse.created(URI.create("/retire/".concat(retireCreate.getId())))
                .contentType(APPLICATION_JSON)
                .bodyValue(retireCreate))
                .onErrorResume(e -> Mono.error(new RuntimeException(e.getMessage())));
    }

    public Mono<ServerResponse> createRetire(ServerRequest request){
        Mono<Retire> retire = request.bodyToMono(Retire.class);
        return retire.flatMap(retireRequest ->  billService.findByAccountNumber(retireRequest.getBill().getAccountNumber())
                        .flatMap(billR -> {
                            billR.setBalance(billR.getBalance() - retireRequest.getAmount());
                            if (retireRequest.getAmount() > billR.getBalance()){
                                return Mono.empty();
                            }
                            return Mono.just(billR);
                        }).switchIfEmpty(Mono.error(new RuntimeException("The retire amount exceeds the available balance")))
                        .flatMap(bilTransaction -> {
                            Transaction transaction = new Transaction();
                            transaction.setTransactionType("RETIRE");
                            transaction.setTransactionAmount(retireRequest.getAmount());
                            transaction.setBill(bilTransaction);
                            transaction.setDescription(retireRequest.getDescription());
                            return transactionService.createTransaction(transaction);
                        })
                        .flatMap(currentTransaction -> {
                            log.info("CURRENT_TRANSACTION: {}", currentTransaction);
                            retireRequest.setBill(currentTransaction.getBill());
                            return retireService.create(retireRequest);
                        })).flatMap(retireUpdate -> ServerResponse.created(URI.create("/retire/".concat(retireUpdate.getId())))
                        .contentType(APPLICATION_JSON)
                        .bodyValue(retireUpdate))
                .onErrorResume(e -> Mono.error(new RuntimeException(e.getMessage())));
    }

    public Mono<ServerResponse> save(ServerRequest request){
        Mono<Retire> product = request.bodyToMono(Retire.class);
        return product.flatMap(retireService::create)
                .flatMap(p -> ServerResponse.created(URI.create("/retire/".concat(p.getId())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(p))
                .onErrorResume(error -> {
                    WebClientResponseException errorResponse = (WebClientResponseException) error;
                    if(errorResponse.getStatusCode() == HttpStatus.BAD_REQUEST) {
                        return ServerResponse.badRequest()
                                .contentType(MediaType.APPLICATION_JSON)
                                .bodyValue(errorResponse.getResponseBodyAsString());
                    }
                    return Mono.error(errorResponse);
                });
    }

    private Mono<ServerResponse> errorHandler(Mono<ServerResponse> response){
        return response.onErrorResume(error -> {
            WebClientResponseException errorResponse = (WebClientResponseException) error;
            if(errorResponse.getStatusCode() == HttpStatus.NOT_FOUND) {
                Map<String, Object> body = new HashMap<>();
                body.put("error", "the retire does not exist: ".concat(errorResponse.getMessage()));
                body.put("timestamp", new Date());
                body.put("status", errorResponse.getStatusCode().value());
                return ServerResponse.status(HttpStatus.NOT_FOUND).bodyValue(body);
            }
            return Mono.error(errorResponse);
        });
    }
}
