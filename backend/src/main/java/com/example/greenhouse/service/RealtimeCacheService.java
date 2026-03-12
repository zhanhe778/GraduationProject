package com.example.greenhouse.service;

import com.example.greenhouse.Entity.SensorDataEntity;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * 实时数据缓存服务
 * 负责将传感器数据缓存到 Redis 中，支持最新数据和时间窗口数据
 * 
 * Redis 在本项目中的主要作用：
 * 1. 缓存最新传感器数据，提供快速读取
 * 2. 维护时间窗口数据，用于趋势分析
 * 3. 减少数据库查询压力
 * 4. 支持高并发场景下的实时数据访问
 */
@Service
@RequiredArgsConstructor
public class RealtimeCacheService {

    // Redis 操作模板
    private final StringRedisTemplate redisTemplate;
    
    // JSON 序列化工具
    private final ObjectMapper objectMapper;

    // 时间窗口最大大小：最多保留最近 100 条数据
    private static final int WINDOW_MAX_SIZE = 100;

    /**
     * 缓存传感器读数
     * 1. 缓存最新数据（Hash 结构）
     * 2. 维护时间窗口数据（List 结构）
     * 
     * @param reading 传感器数据实体
     */
    public void cacheReading(SensorDataEntity reading) {
        // 最新数据缓存键：env:{greenhouseId}:{sensorId}:latest
        String latestKey = "env:" + reading.getGreenhouseId() + ":" + reading.getSensorId() + ":latest";
        Map<String, String> latest = new HashMap<>();
        latest.put("type", reading.getType());
        latest.put("value", Double.toString(reading.getValue()));
        latest.put("unit", reading.getUnit() == null ? "" : reading.getUnit());
        latest.put("timestamp", reading.getTimestamp().toString());
        redisTemplate.opsForHash().putAll(latestKey, latest);

        // 时间窗口数据缓存键：env:{greenhouseId}:{sensorId}:win
        String windowKey = "env:" + reading.getGreenhouseId() + ":" + reading.getSensorId() + ":win";
        try {
            // 将传感器数据序列化为 JSON 字符串
            String json = objectMapper.writeValueAsString(reading);
            // 将 JSON 字符串添加到列表末尾
            redisTemplate.opsForList().rightPush(windowKey, json);
            // 获取列表当前大小
            Long size = redisTemplate.opsForList().size(windowKey);
            // 如果列表大小超过最大值，修剪列表，只保留最后 WINDOW_MAX_SIZE 条数据
            if (size != null && size > WINDOW_MAX_SIZE) {
                redisTemplate.opsForList().trim(windowKey, size - WINDOW_MAX_SIZE, -1);
            }
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize reading", e);
        }
    }

    /**
     * 加载时间窗口数据
     * 获取指定传感器的最近 WINDOW_MAX_SIZE 条数据
     * 
     * @param greenhouseId 大棚 ID
     * @param sensorId 传感器 ID
     * @return 传感器数据列表
     */
    public List<SensorDataEntity> loadWindow(String greenhouseId, String sensorId) {
        String windowKey = "env:" + greenhouseId + ":" + sensorId + ":win";
        // 获取列表中所有数据
        List<String> values = redisTemplate.opsForList().range(windowKey, 0, -1);
        List<SensorDataEntity> result = new ArrayList<>();
        if (values == null) {
            return result;
        }
        // 将 JSON 字符串反序列化为 SensorDataEntity 对象
        for (String v : values) {
            try {
                SensorDataEntity reading = objectMapper.readValue(v, SensorDataEntity.class);
                result.add(reading);
            } catch (JsonProcessingException ignored) {
                // 忽略反序列化失败的数据
            }
        }
        return result;
    }

    /**
     * 加载最新传感器数据
     * 获取指定传感器的最后一条数据
     * 
     * @param greenhouseId 大棚 ID
     * @param sensorId 传感器 ID
     * @return 最新传感器数据
     */
    public SensorDataEntity loadLatest(String greenhouseId, String sensorId) {
        String windowKey = "env:" + greenhouseId + ":" + sensorId + ":win";
        // 获取列表最后一个元素（最新数据）
        String json = redisTemplate.opsForList().index(windowKey, -1);
        if (json == null) {
            return null;
        }
        try {
            // 将 JSON 字符串反序列化为 SensorDataEntity 对象
            return objectMapper.readValue(json, SensorDataEntity.class);
        } catch (JsonProcessingException e) {
            return null;
        }
    }
}
