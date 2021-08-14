package com.example.msretire.models.entities;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

@JsonIgnoreProperties(ignoreUnknown = true)
@Data
public class Bill {

    @Field(name = "accountNumber")
    private String accountNumber;

    @Field(name = "balance")
    private Double balance;

    @Field(name = "acquisition")
    private Acquisition acquisition;

}
