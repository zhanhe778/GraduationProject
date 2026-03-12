package com.example.greenhouse.dto;

import java.time.Instant;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class SensorHistoryDto {
    String sensorId;
    String type;
    double value;
    String unit;
    Instant timestamp;
}

