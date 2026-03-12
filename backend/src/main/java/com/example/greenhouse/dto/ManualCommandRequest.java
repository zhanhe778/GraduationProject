package com.example.greenhouse.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ManualCommandRequest {
    @NotBlank
    private String action;
    private String reason;
}

