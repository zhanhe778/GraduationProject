package com.example.greenhouse.service;

import com.example.greenhouse.Entity.ActuatorCommandEntity;
import com.example.greenhouse.Entity.ActuatorEntity;
import com.example.greenhouse.Mapper.ActuatorCommandMapper;
import com.example.greenhouse.Mapper.ActuatorMapper;
import com.example.greenhouse.dto.RealtimeEvent;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ManualControlService {

    private final ActuatorMapper actuatorMapper;
    private final ActuatorCommandMapper actuatorCommandMapper;
    private final ManualModeService manualModeService;
    private final MqttCommandPublisher mqttCommandPublisher;
    private final SimpMessagingTemplate messagingTemplate;

    @Transactional
    public ActuatorCommandEntity sendManualCommand(String greenhouseId, String actuatorId, String action, String reason) {
        // 1. 验证是否处于手动模式
        if (!manualModeService.isManualModeEnabled(greenhouseId)) {
            throw new IllegalStateException("当前为自动模式，无法执行手动操作，请先开启手动模式");
        }
        
        ActuatorEntity actuator = actuatorMapper.findByGreenhouseIdAndActuatorId(greenhouseId, actuatorId)
                .orElseThrow(() -> new IllegalArgumentException("Actuator not found"));
        ActuatorCommandEntity command = ActuatorCommandEntity.builder()
                .commandId(buildCommandId(actuatorId))
                .actuatorId(actuatorId)
                .greenhouseId(greenhouseId)
                .type(actuator.getType())
                .action(action)
                .reason(reason)
                .timestamp(Instant.now())
                .source(ActuatorCommandEntity.Source.MANUAL)
                .build();
        actuatorCommandMapper.save(command);
        actuator.setStatus(action);
        actuator.setLastCommandAt(command.getTimestamp());
        actuatorMapper.save(actuator);
        mqttCommandPublisher.publish(command);
        messagingTemplate.convertAndSend("/topic/greenhouse/" + greenhouseId,
                RealtimeEvent.builder().kind("actuator").payload(actuator).build());
        return command;
    }

    private String buildCommandId(String actuatorId) {
        return "cmd_" + DateTimeFormatter.ofPattern("yyyyMMddHHmmss")
                .withZone(ZoneId.systemDefault())
                .format(Instant.now())
                + "_" + actuatorId;
    }
}
