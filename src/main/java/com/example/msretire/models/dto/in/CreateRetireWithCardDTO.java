package com.example.msretire.models.dto.in;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@JsonIgnoreProperties(ignoreUnknown = true)
@Data
public class CreateRetireWithCardDTO {
    private String cardNumber;
    private Double amount;
    private String description;
}
