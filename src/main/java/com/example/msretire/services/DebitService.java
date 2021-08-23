package com.example.msretire.services;

import com.example.msretire.exception.webclient.ArgumentWebClientNotValid;
import com.example.msretire.models.entities.Bill;
import com.example.msretire.models.entities.Debit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.Collections;

import static org.springframework.http.MediaType.APPLICATION_JSON;

@Service
public class DebitService {
    private final WebClient.Builder webClientBuilder;

    Logger logger = LoggerFactory.getLogger(DebitService.class);

    @Autowired
    public DebitService(WebClient.Builder webClientBuilder) {
        this.webClientBuilder = webClientBuilder;
    }

    public Mono<Debit> findByCardNumber(String cardNumber) {
        return webClientBuilder
                .baseUrl("http://SERVICE-DEBIT/debit")
                .build()
                .get()
                .uri("/card/{cardNumber}", Collections.singletonMap("cardNumber", cardNumber))
                .accept(APPLICATION_JSON)
                .retrieve()
                .onStatus(HttpStatus::isError, response -> {
                    logTraceResponse(logger, response);
                    return Mono.error(new ArgumentWebClientNotValid(
                            String.format("THE CARD NUMBER DONT EXIST IN MICRO SERVICE DEBIT -> %s", cardNumber)
                    ));
                })
                .bodyToMono(Debit.class);
    }

    public Mono<Debit> updateDebit(Debit debit){
        return webClientBuilder
                .baseUrl("http://SERVICE-DEBIT/debit")
                .build()
                .post()
                .uri("/update")
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .body(Mono.just(debit), Debit.class)
                .retrieve()
                .onStatus(HttpStatus::isError, response -> {
                    logTraceResponse(logger, response);
                    return Mono.error(new RuntimeException("THE DEBIT UPDATE FAILED"));
                })
                .bodyToMono(Debit.class);
    }

    public static void logTraceResponse(Logger log, ClientResponse response) {
        if (log.isTraceEnabled()) {
            log.trace("Response status: {}", response.statusCode());
            log.trace("Response headers: {}", response.headers().asHttpHeaders());
            response.bodyToMono(String.class)
                    .publishOn(Schedulers.boundedElastic())
                    .subscribe(body -> log.trace("Response body: {}", body));
        }
    }
}
