package com.example.msretire.topic.consumer;

import com.example.msretire.handler.RetireHandler;
import com.example.msretire.models.dto.in.CreateRetireWithCardDTO;
import com.example.msretire.models.entities.Acquisition;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;

@Slf4j
@Component
public class retireConsumer {
    private static final String SERVICE_CREATE_RETIRE_TOPIC = "service-create-retire-topic";
    private final static String GROUP_ID = "retire-group";
    private final RetireHandler retireHandler;
    private final ObjectMapper objectMapper;

    @Autowired
    public retireConsumer(RetireHandler retireHandler, ObjectMapper objectMapper) {
        this.retireHandler = retireHandler;
        this.objectMapper = objectMapper;
    }

    @KafkaListener( topics = SERVICE_CREATE_RETIRE_TOPIC, groupId = GROUP_ID)
    public Disposable retrieveSavedAcquisition(String data) throws Exception {
        log.info("data from kafka listener (acquisition) =>"+data);
        CreateRetireWithCardDTO retireWithCardDTO= objectMapper.readValue(data, CreateRetireWithCardDTO.class );

        return Mono.just(retireWithCardDTO)
                .as(retireHandler::createRetire)
                .log()
                .subscribe();
    }
}
