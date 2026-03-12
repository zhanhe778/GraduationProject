package com.example.greenhouse.dto;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class RuleRangeDto {
    String metric;
    double minValue;
    double maxValue;
    boolean enabled;
}

