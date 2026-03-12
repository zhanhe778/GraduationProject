package com.example.greenhouse.dto;

import lombok.Data;

@Data
public class SensorMqttPayload {
    private String sensorId;
    private String greenhouseId;
    private String type;
    private double value;
    private String unit;
    private String timestamp;
}

