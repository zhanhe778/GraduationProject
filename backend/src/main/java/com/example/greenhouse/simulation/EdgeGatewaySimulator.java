package com.example.greenhouse.simulation;

import com.example.greenhouse.dto.SensorMqttPayload;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

/**
 * 独立的边缘网关模拟器 (Standalone Edge Gateway Simulator)
 * 
 * 功能：
 * 1. 监听 raw/sensor/# 主题 (模拟接收传感器原始数据)
 * 2. 对数据进行过滤 (心跳 + 阈值判断)
 * 3. 将有效数据转发到 sensor/# 主题 (模拟上传到后端)
 * 
 * 运行方式：
 * 在单独的终端运行此 Main 方法
 */
public class EdgeGatewaySimulator {

    private static final String BROKER_URL = "tcp://localhost:1883";
    private static final String CLIENT_ID = "edge-gateway-simulator-" + UUID.randomUUID().toString().substring(0, 8);
    private static final String RAW_TOPIC = "raw/sensor/#";
    
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final Map<String, SensorState> sensorCache = new ConcurrentHashMap<>();
    private static final AtomicLong totalRaw = new AtomicLong();
    private static final AtomicLong totalForwarded = new AtomicLong();

    // 过滤配置
    private static final double VALUE_THRESHOLD_PERCENT = 0.05; // 5% 变化阈值
    private static final long MAX_INTERVAL_SECONDS = 60; // 60秒心跳
    private static final long MIN_INTERVAL_SECONDS = 1;  // 1秒限流

    static class SensorState {
        Double lastValue;
        Instant lastReportTime;
    }

    public static void main(String[] args) {
        try {
            System.out.println("Starting Edge Gateway Simulator...");
            MqttClient client = new MqttClient(BROKER_URL, CLIENT_ID, new MemoryPersistence());
            MqttConnectOptions options = new MqttConnectOptions();
            options.setAutomaticReconnect(true);
            options.setCleanSession(true);
            options.setConnectionTimeout(10);

            client.setCallback(new MqttCallback() {
                @Override
                public void connectionLost(Throwable cause) {
                    System.err.println("Connection lost: " + cause.getMessage());
                }

                @Override
                public void messageArrived(String topic, MqttMessage message) throws Exception {
                    processMessage(client, topic, message);
                }

                @Override
                public void deliveryComplete(IMqttDeliveryToken token) {
                    // ignore
                }
            });

            client.connect(options);
            System.out.println("Connected to Broker: " + BROKER_URL);
            
            client.subscribe(RAW_TOPIC);
            System.out.println("Subscribed to: " + RAW_TOPIC);
            System.out.println("Gateway is running. Forwarding filtered data to sensor/...");
            
            // Keep alive
            synchronized (EdgeGatewaySimulator.class) {
                EdgeGatewaySimulator.class.wait();
            }
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void processMessage(MqttClient client, String topic, MqttMessage message) {
        try {
            long rawCount = totalRaw.incrementAndGet();
            String payloadJson = new String(message.getPayload(), StandardCharsets.UTF_8);
            // System.out.println("Received RAW: " + topic + " -> " + payloadJson);

            // topic format: raw/sensor/{greenhouseId}/{type}/{sensorId}
            String[] parts = topic.split("/");
            if (parts.length < 5) return;
            
            String greenhouseId = parts[2];
            String type = parts[3];
            String sensorId = parts[4];
            
            SensorMqttPayload payload = objectMapper.readValue(payloadJson, SensorMqttPayload.class);
            if (payload == null) return;

            String cacheKey = greenhouseId + ":" + sensorId;
            SensorState state = sensorCache.computeIfAbsent(cacheKey, k -> new SensorState());

            boolean shouldReport = false;
            Instant now = Instant.now();

            if (state.lastReportTime == null) {
                shouldReport = true;
            } else {
                long secondsSinceLast = now.getEpochSecond() - state.lastReportTime.getEpochSecond();
                
                if (secondsSinceLast < MIN_INTERVAL_SECONDS) {
                    return; // Rate limit
                }

                if (secondsSinceLast >= MAX_INTERVAL_SECONDS) {
                    shouldReport = true;
                    System.out.println("Heartbeat: " + sensorId);
                } else {
                    double lastVal = state.lastValue != null ? state.lastValue : 0.0;
                    double currentVal = payload.getValue();
                    double diff = Math.abs(currentVal - lastVal);
                    
                    if (Math.abs(lastVal) < 0.0001) {
                        if (diff > 0.01) shouldReport = true;
                    } else {
                        double changePercent = diff / Math.abs(lastVal);
                        if (changePercent >= VALUE_THRESHOLD_PERCENT) {
                            shouldReport = true;
                            System.out.printf("Significant Change (%.2f%%): %s%n", changePercent * 100, sensorId);
                        }
                    }
                }
            }

            if (shouldReport) {
                long forwardedCount = totalForwarded.incrementAndGet();
                state.lastValue = payload.getValue();
                state.lastReportTime = now;

                // Forward to clean topic: sensor/{greenhouseId}/{type}/{sensorId}
                String targetTopic = String.format("sensor/%s/%s/%s", greenhouseId, type, sensorId);
                
                MqttMessage forwardMessage = new MqttMessage(message.getPayload());
                forwardMessage.setQos(1);
                // Retained should be false for sensor data stream to avoid stale data on reconnect
                forwardMessage.setRetained(false); 
                client.publish(targetTopic, forwardMessage);
                System.out.println("Forwarded: " + targetTopic);
                
                long filteredCount = rawCount - forwardedCount;
                double filteredPercent = rawCount == 0 ? 0.0 : (filteredCount * 100.0 / rawCount);
                System.out.printf(
                        "Stats => Raw: %d, Forwarded: %d, Filtered: %d (%.2f%%)%n",
                        rawCount, forwardedCount, filteredCount, filteredPercent
                );
            }

        } catch (Exception e) {
            System.err.println("Error processing message: " + e.getMessage());
        }
    }
}
