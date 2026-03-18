package com.example.greenhouse.simulation;

import com.example.greenhouse.dto.SensorMqttPayload;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Random;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

/**
 * 传感器数据模拟程序
 *
 * 功能说明：
 * 1. 在代码顶部集中定义“日期”和三个 MQTT Topic，方便后期修改；
 * 2. 生成指定日期内 24 小时、每 5 分钟一次的温度、湿度、光照数据；
 * 3. 实际发送时以 1 秒为间隔快速回放（即 1 秒模拟 5 分钟）；
 * 4. 数据发送到 raw/sensor/... 主题，由 EdgeGatewaySimulator 进行过滤。
 */
public class SensorDataSimulator {

    // ========================= 基本配置区域 =========================

    /** 要模拟的日期（本地日期，不含时区），可按需修改 */
    private static final LocalDate BASE_DATE = LocalDate.of(2026, 3, 1);

    /** 时区（用于生成带时区的 ISO-8601 时间戳） */
    private static final ZoneId ZONE_ID = ZoneId.of("Asia/Shanghai");

    /** MQTT Broker 地址（需与 EMQX 配置一致） */
    private static final String BROKER_URL = "tcp://localhost:1883";

    /** 温室 ID */
    private static final String GREENHOUSE_ID = "gh001";

    /** 三个传感器的 raw topic，结构：raw/sensor/{greenhouseId}/{type}/{sensorId} */
    private static final String RAW_TOPIC_TEMPERATURE =
            "raw/sensor/" + GREENHOUSE_ID + "/temperature/temp_01";
    private static final String RAW_TOPIC_HUMIDITY =
            "raw/sensor/" + GREENHOUSE_ID + "/humidity/humi_01";
    private static final String RAW_TOPIC_LIGHT =
            "raw/sensor/" + GREENHOUSE_ID + "/light/light_01";

    /** 逻辑时间：每 5 分钟一条数据；实际时间：每 1 秒发送一条数据 */
    private static final int LOGICAL_INTERVAL_MINUTES = 5;
    private static final long SEND_INTERVAL_MILLIS = 1000L;

    // ===============================================================

    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ISO_OFFSET_DATE_TIME;
    private static final Random random = new Random();

    public static void main(String[] args) {
        MqttClient client = null;
        try {
            // 1. 创建 MQTT 客户端
            String clientId = "sensor-data-simulator-" + System.currentTimeMillis();
            client = new MqttClient(BROKER_URL, clientId, new MemoryPersistence());

            // 2. 配置连接参数
            MqttConnectOptions options = new MqttConnectOptions();
            options.setAutomaticReconnect(true);
            options.setCleanSession(true);
            options.setConnectionTimeout(10);

            System.out.println("Connecting to broker: " + BROKER_URL);
            client.connect(options);
            System.out.println("Connected. Start sending simulated data...");

            // 3. 生成 24 小时内每 5 分钟一次的时间点
            LocalDateTime startTime = BASE_DATE.atStartOfDay();
            int totalSlots = (24 * 60) / LOGICAL_INTERVAL_MINUTES;

            for (int i = 0; i < totalSlots; i++) {
                // 当前逻辑时间 = 起始时间 + i * 5 分钟
                LocalDateTime logicalTime = startTime.plusMinutes((long) i * LOGICAL_INTERVAL_MINUTES);
                ZonedDateTime zonedDateTime = logicalTime.atZone(ZONE_ID);
                String timestamp = ISO_FORMATTER.format(zonedDateTime);

                // 4. 生成三类传感器值，并进行格式化：
                //    - 温度、湿度：保留 1 位小数
                //    - 光照：取整
                double temperature = roundToOneDecimal(generateTemperature(logicalTime));
                double humidity = roundToOneDecimal(generateHumidity(logicalTime));
                double light = Math.round(generateLight(logicalTime));

                // 5. 构造并发送三条 MQTT 消息到 raw topic
                publishSensorReading(
                        client,
                        RAW_TOPIC_TEMPERATURE,
                        buildPayload("temp_01", "temperature", "℃", temperature, timestamp)
                );

                publishSensorReading(
                        client,
                        RAW_TOPIC_HUMIDITY,
                        buildPayload("humi_01", "humidity", "%", humidity, timestamp)
                );

                publishSensorReading(
                        client,
                        RAW_TOPIC_LIGHT,
                        buildPayload("light_01", "light", "Lux", light, timestamp)
                );

                // 控制台打印当前进度，方便观察
                System.out.printf(
                        "[%d/%d] time=%s  T=%.1f℃  H=%.1f%%  L=%.0fLux%n",
                        i + 1, totalSlots, timestamp, temperature, humidity, light
                );

                // 6. 1 秒后发送下一批（模拟 5 分钟）
                Thread.sleep(SEND_INTERVAL_MILLIS);
            }

            System.out.println("All simulated data sent. Total slots: " + totalSlots);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (client != null && client.isConnected()) {
                try {
                    client.disconnect();
                    client.close();
                } catch (Exception ignored) {
                }
            }
        }
    }

    /**
     * 根据时间生成一个较为自然的温度值（白天高、夜间低，带少量噪声）。
     */
    private static double generateTemperature(LocalDateTime time) {
        int hour = time.getHour();
        double base = (hour >= 9 && hour <= 18) ? 25.0 : 18.0;
        double wave = 3.0 * Math.sin((hour / 24.0) * 2 * Math.PI);
        double noise = random.nextGaussian() * 0.3;
        return base + wave + noise;
    }

    /**
     * 根据时间生成湿度值（白天略低，夜间略高，限制在 30~95 范围）。
     */
    private static double generateHumidity(LocalDateTime time) {
        int hour = time.getHour();
        double base = (hour >= 10 && hour <= 17) ? 55.0 : 70.0;
        double wave = 5.0 * Math.cos((hour / 24.0) * 2 * Math.PI);
        double noise = random.nextGaussian() * 1.5;
        double value = base + wave + noise;
        return Math.max(30.0, Math.min(95.0, value));
    }

    /**
     * 根据时间生成光照值（Lux），夜间接近 0，中午最强。
     */
    private static double generateLight(LocalDateTime time) {
        int hour = time.getHour();
        if (hour < 6 || hour > 19) {
            return 0.0;
        }
        double center = 13.0; // 午后 1 点附近最强
        double diff = hour - center;
        double maxLux = 50000.0;
        double value = maxLux * Math.exp(-diff * diff / (2 * 4));
        double noise = random.nextGaussian() * 20.0;
        return Math.max(0.0, value + noise);
    }

    /**
     * 将数值保留 1 位小数。
     */
    private static double roundToOneDecimal(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    /**
     * 构造一个 SensorMqttPayload 对象。
     */
    private static SensorMqttPayload buildPayload(
            String sensorId,
            String type,
            String unit,
            double value,
            String timestamp
    ) {
        SensorMqttPayload payload = new SensorMqttPayload();
        payload.setSensorId(sensorId);
        payload.setGreenhouseId(GREENHOUSE_ID);
        payload.setType(type);
        payload.setUnit(unit);
        payload.setValue(value);
        payload.setTimestamp(timestamp);
        return payload;
    }

    /**
     * 将 payload 序列化为 JSON，并发布到指定 raw topic。
     */
    private static void publishSensorReading(
            MqttClient client,
            String topic,
            SensorMqttPayload payload
    ) throws Exception {
        String json = objectMapper.writeValueAsString(payload);
        MqttMessage message = new MqttMessage(json.getBytes(StandardCharsets.UTF_8));
        message.setQos(1);
        client.publish(topic, message);
    }
}

