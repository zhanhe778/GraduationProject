package com.example.greenhouse.dto;

import java.time.Instant;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class SensorStatusDto {
    String greenhouseId;
    String sensorId;
    String type;
    Double value;
    String unit;
    Instant timestamp;
}

