package com.example.greenhouse.dto;

import java.time.Instant;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ActuatorStatusDto {
    String greenhouseId;
    String actuatorId;
    String type;
    String status;
    Instant lastCommandAt;
}

