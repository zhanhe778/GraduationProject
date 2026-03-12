package com.example.greenhouse.dto;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import lombok.Data;

@Data
public class RuleRangeBatchRequest {
    @NotEmpty
    private List<RuleRangeDto> rules;
}

