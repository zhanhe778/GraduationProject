package com.example.greenhouse.service;

import com.example.greenhouse.Entity.ActuatorCommandEntity;
import com.example.greenhouse.Entity.ActuatorEntity;
import com.example.greenhouse.Mapper.ActuatorCommandMapper;
import com.example.greenhouse.Mapper.ActuatorMapper;
import com.example.greenhouse.dto.ActuatorMqttPayload;
import com.example.greenhouse.dto.RealtimeEvent;
import com.example.greenhouse.dto.SensorMqttPayload;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.integration.mqtt.support.MqttHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MqttIngestionService {

    private static final Logger logger = LoggerFactory.getLogger(MqttIngestionService.class);

    private final ObjectMapper objectMapper;
    private final SensorDataService sensorDataService;
    private final ActuatorMapper actuatorMapper;
    private final ActuatorCommandMapper actuatorCommandMapper;
    private final SimpMessagingTemplate messagingTemplate;

    /**
     * 处理 MQTT 接收到的消息
     * 该方法通过 @ServiceActivator 注解绑定到 "mqttInboundChannel" 通道
     * 当有 MQTT 消息到达时，Spring Integration 会自动调用此方法
     */
    @ServiceActivator(inputChannel = "mqttInboundChannel")
    public void handleMessage(Message<?> message) {
        // 获取 MQTT 主题 (Topic)
        String topic = (String) message.getHeaders().get(MqttHeaders.RECEIVED_TOPIC);
        if (topic == null) {
            return;
        }

        logger.info("MQTT inbound topic={}, payload={}", topic, toPayloadString(message.getPayload()));
        
        // 解析 Topic 结构
        // 假设 Sensor Topic 格式: sensor/{greenhouseId}/{sensorType}/{sensorId}
        // 假设 Actuator Topic 格式: actuator/{greenhouseId}/{actuatorType}/{actuatorId}/command
        String[] parts = topic.split("/");
        
        // 处理传感器数据
        if (parts.length >= 4 && "sensor".equalsIgnoreCase(parts[0])) {
            handleSensor(topic, parts, message);
            return;
        }
        
        // 处理执行器反馈/命令
        if (parts.length >= 5 && "actuator".equalsIgnoreCase(parts[0])) {
            handleActuator(topic, parts, message);
            return;
        }
        logger.warn("MQTT inbound topic ignored: {}", topic);
    }

    /**
     * 处理传感器数据消息
     * 1. 解析 payload 为 SensorMqttPayload 对象
     * 2. 调用 sensorDataService 进行数据存储和规则评估
     */
    private void handleSensor(String topic, String[] parts, Message<?> message) {
        String greenhouseId = parts[1];
        String sensorType = parts[2];
        String sensorId = parts[3];
        
        // 将消息内容转换为字符串
        String payloadText = toPayloadString(message.getPayload());
        // JSON 反序列化
        SensorMqttPayload payload = parsePayload(payloadText, SensorMqttPayload.class);
        
        if (payload == null) {
            logger.warn("MQTT sensor payload parse failed. topic={}, payload={}", topic, payloadText);
            return;
        }
        logger.info("MQTT sensor parsed greenhouseId={}, sensorId={}, type={}", greenhouseId, sensorId, sensorType);
        
        // 存储数据并触发后续流程（如 WebSocket 推送、规则引擎）
        sensorDataService.ingestFromMqtt(payload, greenhouseId, sensorType, sensorId);
    }

    /**
     * 处理执行器相关消息
     * 主要用于更新执行器的当前状态，并记录命令执行历史
     */
    private void handleActuator(String topic, String[] parts, Message<?> message) {
        String greenhouseId = parts[1];
        String actuatorType = parts[2];
        String actuatorId = parts[3];
        
        String payloadText = toPayloadString(message.getPayload());
        ActuatorMqttPayload payload = parsePayload(payloadText, ActuatorMqttPayload.class);
        
        if (payload == null) {
            logger.warn("MQTT actuator payload parse failed. topic={}, payload={}", topic, payloadText);
        }
        
        // 解析时间戳，如果缺失则使用当前时间
        Instant timestamp = parseTimestamp(payload != null ? payload.getTimestamp() : null);
        if (timestamp == null) {
            timestamp = Instant.now();
        }
        
        // 查找或新建执行器实体
        ActuatorEntity actuator = actuatorMapper.findByGreenhouseIdAndActuatorId(greenhouseId, actuatorId)
                .orElseGet(() -> ActuatorEntity.builder()
                        .greenhouseId(greenhouseId)
                        .actuatorId(actuatorId)
                        .type(actuatorType)
                        .build());
                        
        // 更新执行器状态
        actuator.setType(actuatorType);
        if (payload != null && payload.getAction() != null) {
            actuator.setStatus(payload.getAction());
        }
        actuator.setLastCommandAt(timestamp);
        actuatorMapper.save(actuator);
        
        // 通过 WebSocket 推送执行器最新状态给前端
        messagingTemplate.convertAndSend("/topic/greenhouse/" + greenhouseId,
                RealtimeEvent.builder().kind("actuator").payload(actuator).build());
        
        // 如果消息中包含 commandId，说明这是一条命令执行反馈，记录到命令历史表
        if (payload != null && payload.getCommandId() != null) {
            boolean exists = actuatorCommandMapper.findByCommandId(payload.getCommandId()).isPresent();
            if (!exists) {
                ActuatorCommandEntity command = ActuatorCommandEntity.builder()
                        .commandId(payload.getCommandId())
                        .actuatorId(actuatorId)
                        .greenhouseId(greenhouseId)
                        .type(actuatorType)
                        .action(payload.getAction() == null ? "unknown" : payload.getAction())
                        .reason(payload.getReason())
                        .timestamp(timestamp)
                        .source(ActuatorCommandEntity.Source.AUTO)
                        .build();
                actuatorCommandMapper.save(command);
            }
        }
    }

    // 辅助方法：JSON 反序列化
    private <T> T parsePayload(String payloadText, Class<T> clazz) {
        try {
            return objectMapper.readValue(payloadText, clazz);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    // 辅助方法：将消息载荷转换为字符串
    private String toPayloadString(Object payload) {
        if (payload instanceof String text) {
            return text;
        }
        if (payload instanceof byte[] bytes) {
            return new String(bytes, StandardCharsets.UTF_8);
        }
        return payload.toString();
    }

    // 辅助方法：解析 ISO-8601 时间戳
    private Instant parseTimestamp(String timestamp) {
        if (timestamp == null || timestamp.isBlank()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(timestamp).toInstant();
        } catch (Exception ignored) {
            return null;
        }
    }
}
