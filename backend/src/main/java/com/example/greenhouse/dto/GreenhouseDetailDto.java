package com.example.greenhouse.dto;

import java.util.List;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class GreenhouseDetailDto {
    String greenhouseId;
    String name;
    boolean manualMode;
    List<SensorStatusDto> sensors;
    List<ActuatorStatusDto> actuators;
    List<RuleRangeDto> rules;
}

