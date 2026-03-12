package com.example.greenhouse.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ManualModeRequest {
    @NotNull
    private Boolean enabled;
}

