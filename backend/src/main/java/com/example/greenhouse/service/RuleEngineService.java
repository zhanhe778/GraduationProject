package com.example.greenhouse.service;

import com.example.greenhouse.Entity.ActuatorCommandEntity;
import com.example.greenhouse.Entity.ActuatorEntity;
import com.example.greenhouse.Entity.RuleRangeEntity;
import com.example.greenhouse.Entity.SensorDataEntity;
import com.example.greenhouse.Entity.SensorEntity;
import com.example.greenhouse.Mapper.ActuatorCommandMapper;
import com.example.greenhouse.Mapper.ActuatorMapper;
import com.example.greenhouse.Mapper.RuleRangeMapper;
import com.example.greenhouse.Mapper.SensorMapper;
import com.example.greenhouse.dto.RealtimeEvent;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class RuleEngineService {

    private final RuleRangeMapper ruleRangeMapper;
    private final ActuatorMapper actuatorMapper;
    private final SensorMapper sensorMapper;
    private final ActuatorCommandMapper actuatorCommandMapper;
    private final ManualModeService manualModeService;
    private final MqttCommandPublisher mqttCommandPublisher;
    private final SimpMessagingTemplate messagingTemplate;

    /**
     * 核心规则评估逻辑
     * 当收到新的传感器数据时调用
     */
    @Transactional
    public void evaluate(SensorDataEntity data) {
        // 1. 如果开启了手动模式，则跳过规则判断
        if (manualModeService.isManualModeEnabled(data.getGreenhouseId())) {
            return;
        }
        
        // 2. 查找该大棚对应指标（如温度、湿度）的规则配置
        RuleRangeEntity rule = ruleRangeMapper.findByGreenhouseIdAndMetric(data.getGreenhouseId(), data.getType())
                .orElse(null);
                
        // 3. 如果没有配置规则或规则未启用，直接返回
        if (rule == null || !rule.isEnabled()) {
            return;
        }
        
        double value = data.getValue();
        // 4. 判断数值是否超出范围
        if (value < rule.getMinValue()) {
            // 低于最小值，尝试开启对应设备（如加热器）
            handleOutOfRange(data, rule, false);
        } else if (value > rule.getMaxValue()) {
            // 高于最大值，尝试开启对应设备（如风扇）
            handleOutOfRange(data, rule, true);
        } else {
            // 数值正常，尝试关闭之前因异常而开启的设备
            handleBackToNormal(data, rule);
        }
    }

    /**
     * 重新评估指定大棚的所有规则
     * 通常在规则更新后调用，以确保执行器状态与最新规则保持一致
     */
    @Transactional
    public void reevaluateAll(String greenhouseId) {
        if (manualModeService.isManualModeEnabled(greenhouseId)) {
            return;
        }
        List<SensorEntity> sensors = sensorMapper.findByGreenhouseId(greenhouseId);
        for (SensorEntity sensor : sensors) {
            if (sensor.getLastValue() != null) {
                SensorDataEntity dummyData = SensorDataEntity.builder()
                        .greenhouseId(greenhouseId)
                        .sensorId(sensor.getSensorId())
                        .type(sensor.getType())
                        .value(sensor.getLastValue())
                        .unit(sensor.getUnit())
                        .timestamp(Instant.now())
                        .build();
                evaluate(dummyData);
            }
        }
    }

    /**
     * 处理数值恢复正常的情况
     * 关闭相关的“高限设备”和“低限设备”
     */
    private void handleBackToNormal(SensorDataEntity data, RuleRangeEntity rule) {
        String metric = rule.getMetric().toLowerCase(Locale.ROOT);
        // 关闭可能因高温开启的设备（如 fan）
        turnOffActuator(data, resolveActuatorType(metric, true), metric + " 恢复正常范围");
        // 关闭可能因低温开启的设备（如 heater）
        turnOffActuator(data, resolveActuatorType(metric, false), metric + " 恢复正常范围");
    }

    /**
     * 关闭指定类型的设备
     */
    private void turnOffActuator(SensorDataEntity data, String actuatorType, String reason) {
        if (actuatorType == null) {
            return;
        }
        // 查找所有该类型的设备
        List<ActuatorEntity> actuators = actuatorMapper.findByGreenhouseIdAndType(data.getGreenhouseId(), actuatorType);
        for (ActuatorEntity actuator : actuators) {
            // 只有当前状态为 "on" 时才发送关闭指令，避免重复发送
            if ("on".equalsIgnoreCase(actuator.getStatus())) {
                log.info("RuleEngine: Turning OFF {} due to {}", actuator.getActuatorId(), reason);
                
                // 构建并保存命令记录
                ActuatorCommandEntity command = buildCommand(data, actuator, "off", reason, ActuatorCommandEntity.Source.AUTO);
                actuatorCommandMapper.save(command);
                
                // 更新设备状态
                actuator.setStatus("off");
                actuator.setLastCommandAt(command.getTimestamp());
                actuatorMapper.save(actuator);
                
                // 发送 MQTT 指令到设备
                mqttCommandPublisher.publish(command);
                
                // 通过 WebSocket 通知前端更新状态
                messagingTemplate.convertAndSend("/topic/greenhouse/" + data.getGreenhouseId(),
                        RealtimeEvent.builder().kind("actuator").payload(actuator).build());
            }
        }
    }

    /**
     * 处理数值超限的情况（开启对应设备）
     */
    private void handleOutOfRange(SensorDataEntity data, RuleRangeEntity rule, boolean aboveMax) {
        String metric = rule.getMetric().toLowerCase(Locale.ROOT);
        // 根据指标和超限方向（高/低）决定开启哪种设备
        String actuatorType = resolveActuatorType(metric, aboveMax);
        if (actuatorType == null) {
            return;
        }

        if ("temperature".equals(metric)) {
            String oppositeType = resolveActuatorType(metric, !aboveMax);
            if (oppositeType != null) {
                String oppositeReason;
                if (aboveMax) {
                    oppositeReason = metric + " 高于上限 " + rule.getMaxValue() + "，互斥关闭 " + oppositeType;
                } else {
                    oppositeReason = metric + " 低于下限 " + rule.getMinValue() + "，互斥关闭 " + oppositeType;
                }
                turnOffActuator(data, oppositeType, oppositeReason);
            }
        }
        
        List<ActuatorEntity> actuators = actuatorMapper.findByGreenhouseIdAndType(data.getGreenhouseId(), actuatorType);
        if (actuators.isEmpty()) {
            return;
        }
        // 简化逻辑：只控制找到的第一个设备
        ActuatorEntity actuator = actuators.get(0);
        
        // 防止重复发送开启指令
        if ("on".equalsIgnoreCase(actuator.getStatus())) {
            return;
        }

        String action = "on";
        String reason = buildReason(metric, aboveMax, rule);
        log.info("RuleEngine: Turning ON {} due to {}", actuator.getActuatorId(), reason);

        // 构建命令、保存状态、发送 MQTT、推送 WebSocket
        ActuatorCommandEntity command = buildCommand(data, actuator, action, reason, ActuatorCommandEntity.Source.AUTO);
        actuatorCommandMapper.save(command);
        actuator.setStatus(action);
        actuator.setLastCommandAt(command.getTimestamp());
        actuatorMapper.save(actuator);
        mqttCommandPublisher.publish(command);
        messagingTemplate.convertAndSend("/topic/greenhouse/" + data.getGreenhouseId(),
                RealtimeEvent.builder().kind("actuator").payload(actuator).build());
    }

    /**
     * 根据传感器类型和超限方向，映射到执行器类型
     * 例如：温度高 -> 风扇(fan)；温度低 -> 加热器(heater)
     */
    private String resolveActuatorType(String metric, boolean aboveMax) {
        return switch (metric) {
            case "temperature" -> aboveMax ? "fan" : "heater";
            case "humidity" -> aboveMax ? null : "sprayer"; // 湿度高暂不处理，湿度低开启喷淋
            case "light" -> aboveMax ? "shade" : "light";   // 光照强开启遮阳，光照弱开启补光灯
            default -> null;
        };
    }

    private String buildReason(String metric, boolean aboveMax, RuleRangeEntity rule) {
        if (aboveMax) {
            return metric + " 高于上限 " + rule.getMaxValue();
        }
        return metric + " 低于下限 " + rule.getMinValue();
    }

    // 构建命令实体
    private ActuatorCommandEntity buildCommand(
            SensorDataEntity data,
            ActuatorEntity actuator,
            String action,
            String reason,
            ActuatorCommandEntity.Source source
    ) {
        String commandId = "cmd_" + DateTimeFormatter.ofPattern("yyyyMMddHHmmss")
                .withZone(ZoneId.systemDefault())
                .format(Instant.now())
                + "_" + actuator.getActuatorId();
        return ActuatorCommandEntity.builder()
                .commandId(commandId)
                .actuatorId(actuator.getActuatorId())
                .greenhouseId(actuator.getGreenhouseId())
                .type(actuator.getType())
                .action(action)
                .reason(reason)
                .timestamp(Instant.now())
                .source(source)
                .build();
    }
}
