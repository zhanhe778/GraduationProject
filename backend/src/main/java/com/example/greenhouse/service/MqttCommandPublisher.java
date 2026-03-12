package com.example.greenhouse.service;

import com.example.greenhouse.Entity.ActuatorCommandEntity;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.integration.mqtt.support.MqttHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MqttCommandPublisher {

    private static final Logger logger = LoggerFactory.getLogger(MqttCommandPublisher.class);

    private final MessageChannel mqttOutboundChannel;
    private final ObjectMapper objectMapper;

    public void publish(ActuatorCommandEntity command) {
        String topic = "actuator/" + command.getGreenhouseId()
                + "/" + command.getType()
                + "/" + command.getActuatorId()
                + "/command";
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("commandId", command.getCommandId());
            body.put("actuatorId", command.getActuatorId());
            body.put("greenhouseId", command.getGreenhouseId());
            body.put("type", command.getType());
            body.put("action", command.getAction());
            body.put("reason", command.getReason());
            body.put("timestamp", command.getTimestamp());
            String payload = objectMapper.writeValueAsString(body);
            
            publishToTopic(topic, payload);
            logger.info("Sent MQTT command to topic {} for actuator {}", topic, command.getActuatorId());
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize command payload", e);
        }
    }

    public void publishToTopic(String topic, String payload) {
        Message<String> message = MessageBuilder.withPayload(payload)
                .setHeader(MqttHeaders.TOPIC, topic)
                .build();
        mqttOutboundChannel.send(message);
        logger.info("Sent MQTT message to topic {}", topic);
    }
}
