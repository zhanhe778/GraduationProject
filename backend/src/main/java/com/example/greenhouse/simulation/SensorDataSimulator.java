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

    /**
     * 低频扰动相位：程序启动后固定，用于让曲线更自然但保持连续。
     * 说明：不在每个时间点重新随机，避免“点与点之间突变”。
     */
    private static final double HUMI_PHASE_1 = random.nextDouble() * 2 * Math.PI;
    private static final double HUMI_PHASE_2 = random.nextDouble() * 2 * Math.PI;
    private static final double LIGHT_PHASE_1 = random.nextDouble() * 2 * Math.PI;
    private static final double LIGHT_PHASE_2 = random.nextDouble() * 2 * Math.PI;

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
        // 以“当天分钟进度”作为连续自变量，取值范围 [0, 1)。
        double dayProgress = getDayProgress(time);

        // 主日周期：14:00 左右最高，整体范围控制在 18~35℃。
        // 说明：中心值 26.5，振幅 8.5；采用单一正弦项可获得更平滑的曲线。
        double theta = 2 * Math.PI * (dayProgress - 1.0 / 3.0);
        double value = 26.5 + 8.5 * Math.sin(theta);
        return Math.max(18.0, Math.min(35.0, value));
    }

    /**
     * 根据时间生成湿度值（白天略低，夜间略高，限制在 30~95 范围）。
     */
    private static double generateHumidity(LocalDateTime time) {
        // 与温度形成负相关：温度高时相对湿度通常偏低。
        double dayProgress = getDayProgress(time);

        // 主日周期：范围 30~85，且 14:00 左右为低谷（极值点之一）。
        // 说明：中心值 57.5，振幅 27.5；sin 相位偏移与温度保持一致（14:00 取极值）。
        double dailyCycle = 57.5 - 27.5 * Math.sin(2 * Math.PI * (dayProgress - 1.0 / 3.0));

        // 次级平滑扰动：小幅连续变化，增强自然感，同时不改变整体昼夜趋势。
        double smoothFluctuation =
            0.8 * Math.sin(2 * Math.PI * 1.5 * dayProgress + HUMI_PHASE_1)
                + 0.5 * Math.sin(2 * Math.PI * 2.5 * dayProgress + HUMI_PHASE_2);

        double value = dailyCycle + smoothFluctuation;
        return Math.max(30.0, Math.min(85.0, value));
    }

    /**
     * 计算时间点在当天中的连续进度（0.0 ~ 1.0）。
     * 例：06:00 -> 0.25，12:00 -> 0.5，18:00 -> 0.75。
     */
    private static double getDayProgress(LocalDateTime time) {
        double minutes = time.getHour() * 60.0 + time.getMinute() + time.getSecond() / 60.0;
        return minutes / (24.0 * 60.0);
    }

    /**
     * 根据时间生成光照值（Lux），采用连续曲线：
     * 1) 日出到日落之间按正弦包络变化，正午附近最强；
     * 2) 叠加小幅平滑扰动，模拟云层遮挡等影响；
     * 3) 夜间连续为 0，避免按整点分段导致的台阶突变。
     */
    private static double generateLight(LocalDateTime time) {
        double dayProgress = getDayProgress(time);

        // 设定日照窗口：06:00 ~ 19:30。
        double sunrise = 6.0 / 24.0;
        double sunset = 19.5 / 24.0;

        if (dayProgress <= sunrise || dayProgress >= sunset) {
            return 0.0;
        }

        // 将日照窗口映射到 [0, pi]，窗口边界为 0，中心点为 1（平滑连续）。
        double daylightProgress = (dayProgress - sunrise) / (sunset - sunrise);
        double envelope = Math.sin(Math.PI * daylightProgress);

        double maxLux = 50000.0;
        double base = maxLux * envelope;

        // 低频连续扰动：白天有缓慢起伏，夜间由 envelope 自然压到 0。
        double smoothFluctuation =
                180.0 * Math.sin(2 * Math.PI * 3.0 * dayProgress + LIGHT_PHASE_1)
                        + 90.0 * Math.sin(2 * Math.PI * 5.0 * dayProgress + LIGHT_PHASE_2);

        // 用 envelope 衰减扰动，避免黎明/黄昏附近出现非物理尖峰。
        double value = base + smoothFluctuation * envelope;
        return Math.max(0.0, value);
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

