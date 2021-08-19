package com.example.msretire.models.entities;


import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import org.springframework.data.mongodb.core.mapping.Field;

import java.util.List;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Acquisition {

    private Product product;

    private List<Customer> customerHolder;

    private List<Customer> customerAuthorizedSigner;

    private String iban;
}

