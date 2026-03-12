package com.example.greenhouse.dto;

import lombok.Data;

@Data
public class ActuatorMqttPayload {
    private String commandId;
    private String actuatorId;
    private String greenhouseId;
    private String type;
    private String action;
    private String reason;
    private String timestamp;
}

