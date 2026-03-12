package com.example.greenhouse.service;

import com.example.greenhouse.Entity.GreenhouseEntity;
import com.example.greenhouse.Entity.SensorDataEntity;
import com.example.greenhouse.Entity.SensorEntity;
import com.example.greenhouse.Mapper.GreenhouseMapper;
import com.example.greenhouse.Mapper.SensorDataMapper;
import com.example.greenhouse.Mapper.SensorMapper;
import com.example.greenhouse.dto.RealtimeEvent;
import com.example.greenhouse.dto.SensorMqttPayload;
import com.example.greenhouse.dto.SensorStatusDto;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SensorDataService {

    private static final Logger logger = LoggerFactory.getLogger(SensorDataService.class);

    private final GreenhouseMapper greenhouseMapper;
    private final SensorMapper sensorMapper;
    private final SensorDataMapper sensorDataMapper;
    private final RealtimeCacheService realtimeCacheService;
    private final RuleEngineService ruleEngineService;
    private final SimpMessagingTemplate messagingTemplate;

    /**
     * 接收并处理来自 MQTT 的传感器数据
     * 1. 确保大棚和传感器实体存在
     * 2. 保存传感器历史数据到数据库
     * 3. 更新传感器的最新状态
     * 4. 触发规则引擎
     * 5. 通过 WebSocket 推送实时数据到前端
     */
    @Transactional
    public SensorDataEntity ingestFromMqtt(
            SensorMqttPayload payload,
            String greenhouseId,
            String sensorType,
            String sensorId
    ) {
        // 1. 查找或创建大棚实体
        GreenhouseEntity greenhouse = greenhouseMapper.findByGreenhouseId(greenhouseId)
                .orElseGet(() -> greenhouseMapper.save(GreenhouseEntity.builder()
                        .greenhouseId(greenhouseId)
                        .name(greenhouseId)
                        .build()));

        // 2. 查找或创建传感器实体
        SensorEntity sensor = sensorMapper.findByGreenhouseIdAndSensorId(greenhouseId, sensorId)
                .orElseGet(() -> SensorEntity.builder()
                        .greenhouseId(greenhouseId)
                        .sensorId(sensorId)
                        .type(sensorType)
                        .unit(payload.getUnit())
                        .build());

        // 解析时间戳，如果 payload 中没有时间戳则使用当前时间
        Instant timestamp = parseTimestamp(payload.getTimestamp());
        if (timestamp == null) {
            timestamp = Instant.now();
        }

        // 3. 构建并保存历史数据记录
        SensorDataEntity data = SensorDataEntity.builder()
                .greenhouseId(greenhouse.getGreenhouseId())
                .sensorId(sensor.getSensorId())
                .type(sensorType)
                .value(payload.getValue())
                .unit(payload.getUnit())
                .timestamp(timestamp)
                .build();

        SensorDataEntity saved = sensorDataMapper.save(data);
        logger.info("Sensor data saved greenhouseId={}, sensorId={}, type={}, value={}",
                saved.getGreenhouseId(), saved.getSensorId(), saved.getType(), saved.getValue());

        // 4. 更新传感器最新状态（用于列表页展示）
        sensor.setType(sensorType);
        sensor.setUnit(payload.getUnit());
        sensor.setLastValue(payload.getValue());
        sensor.setLastTimestamp(timestamp);
        sensorMapper.save(sensor);

        // 5. 更新缓存（可选）
        realtimeCacheService.cacheReading(saved);
        
        // 6. 调用规则引擎，评估是否需要自动控制执行器
        ruleEngineService.evaluate(saved);

        // 7. 构建 DTO 并通过 WebSocket 推送实时数据
        SensorStatusDto statusDto = SensorStatusDto.builder()
                .greenhouseId(saved.getGreenhouseId())
                .sensorId(saved.getSensorId())
                .type(saved.getType())
                .value(saved.getValue())
                .unit(saved.getUnit())
                .timestamp(saved.getTimestamp())
                .build();
        messagingTemplate.convertAndSend("/topic/greenhouse/" + greenhouseId,
                RealtimeEvent.builder().kind("sensor").payload(statusDto).build());
        return saved;
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
