# MQTTX 消息模板

本文档提供了在 MQTTX 中测试智能温室平台所需的传感器和执行器消息模板。

---

## 1. 传感器上报模板

### 1.1 温度传感器

**主题**：`sensor/gh001/temperature/temp_01`

**Payload**：
```json
{
  "sensorId": "temp_01",
  "greenhouseId": "gh001",
  "type": "temperature",
  "value": 28.5,
  "unit": "℃",
  "timestamp": "2026-03-02T12:00:00+08:00"
}
```

### 1.2 湿度传感器

**主题**：`sensor/gh001/humidity/humi_01`

**Payload**：
```json
{
  "sensorId": "humi_01",
  "greenhouseId": "gh001",
  "type": "humidity",
  "value": 65.2,
  "unit": "%",
  "timestamp": "2026-03-02T12:00:00+08:00"
}
```

### 1.3 土壤湿度传感器

**主题**：`sensor/gh001/soil/soil_01`

**Payload**：
```json
{
  "sensorId": "soil_01",
  "greenhouseId": "gh001",
  "type": "soil",
  "value": 45.0,
  "unit": "%",
  "timestamp": "2026-03-02T12:00:00+08:00"
}
```

### 1.4 光照传感器

**主题**：`sensor/gh001/light/light_01`

**Payload**：
```json
{
  "sensorId": "light_01",
  "greenhouseId": "gh001",
  "type": "light",
  "value": 500.0,
  "unit": "lux",
  "timestamp": "2026-03-02T12:00:00+08:00"
}
```

---

## 2. 执行器指令模板

### 2.1 风扇控制

**主题**：`actuator/gh001/fan/fan_01/command`

**开启指令**：
```json
{
  "commandId": "cmd_20260302120000_fan_01",
  "actuatorId": "fan_01",
  "greenhouseId": "gh001",
  "type": "fan",
  "action": "on",
  "reason": "温度高于上限 30.0",
  "timestamp": "2026-03-02T12:00:00+08:00"
}
```

**关闭指令**：
```json
{
  "commandId": "cmd_20260302120000_fan_01",
  "actuatorId": "fan_01",
  "greenhouseId": "gh001",
  "type": "fan",
  "action": "off",
  "reason": "温度恢复正常范围",
  "timestamp": "2026-03-02T12:00:00+08:00"
}
```

### 2.2 加热器控制

**主题**：`actuator/gh001/heater/heater_01/command`

**Payload**：
```json
{
  "commandId": "cmd_20260302120000_heater_01",
  "actuatorId": "heater_01",
  "greenhouseId": "gh001",
  "type": "heater",
  "action": "on",
  "reason": "温度低于下限 15.0",
  "timestamp": "2026-03-02T12:00:00+08:00"
}
```

### 2.3 喷淋系统控制

**主题**：`actuator/gh001/sprayer/spray_01/command`

**Payload**：
```json
{
  "commandId": "cmd_20260302120000_spray_01",
  "actuatorId": "spray_01",
  "greenhouseId": "gh001",
  "type": "sprayer",
  "action": "on",
  "reason": "湿度低于下限 40.0",
  "timestamp": "2026-03-02T12:00:00+08:00"
}
```

### 2.4 遮阳系统控制

**主题**：`actuator/gh001/shade/shade_01/command`

**Payload**：
```json
{
  "commandId": "cmd_20260302120000_shade_01",
  "actuatorId": "shade_01",
  "greenhouseId": "gh001",
  "type": "shade",
  "action": "on",
  "reason": "光照高于上限 800.0",
  "timestamp": "2026-03-02T12:00:00+08:00"
}
```

### 2.5 补光灯控制

**主题**：`actuator/gh001/light/light_01/command`

**Payload**：
```json
{
  "commandId": "cmd_20260302120000_light_01",
  "actuatorId": "light_01",
  "greenhouseId": "gh001",
  "type": "light",
  "action": "on",
  "reason": "光照低于下限 200.0",
  "timestamp": "2026-03-02T12:00:00+08:00"
}
```

---

## 3. 执行器状态反馈模板

### 3.1 风扇状态反馈

**主题**：`actuator/gh001/fan/fan_01/status`

**Payload**：
```json
{
  "actuatorId": "fan_01",
  "greenhouseId": "gh001",
  "type": "fan",
  "status": "on",
  "lastCommandAt": "2026-03-02T12:00:00+08:00"
}
```

---

## 4. MQTTX 测试步骤

### 4.1 连接配置

1. **Broker**：`tcp://localhost:1883`
2. **Client ID**：`mqttx-test-123`
3. **用户名**：（留空，默认无认证）
4. **密码**：（留空，默认无认证）
5. **端口**：1883

### 4.2 测试流程

1. **发布传感器数据**：
   - 选择主题 `sensor/gh001/temperature/temp_01`
   - 粘贴温度传感器 Payload
   - 点击发布

2. **订阅执行器指令**：
   - 订阅主题 `actuator/gh001/#`
   - 查看平台自动下发的指令

3. **模拟执行器响应**：
   - 发布主题 `actuator/gh001/fan/fan_01/status`
   - 粘贴执行器状态 Payload

---

## 5. 规则引擎测试场景

### 5.1 温度过高触发风扇

1. 发布温度传感器数据，设置 `value: 35.0`（超过默认上限 30.0）
2. 观察平台自动发布风扇开启指令

### 5.2 温度恢复正常关闭风扇

1. 发布温度传感器数据，设置 `value: 25.0`（回到正常范围）
2. 观察平台自动发布风扇关闭指令

### 5.3 湿度过低触发喷淋

1. 发布湿度传感器数据，设置 `value: 20.0`（低于默认下限 30.0）
2. 观察平台自动发布喷淋开启指令

---

## 6. 注意事项

1. **时间戳格式**：必须使用 ISO-8601 格式，如 `2026-03-02T12:00:00+08:00`
2. **主题格式**：严格按照 `sensor/{greenhouseId}/{sensorType}/{sensorId}` 和 `actuator/{greenhouseId}/{actuatorType}/{actuatorId}/command` 格式
3. **数据类型**：`value` 必须是数字类型，不能用字符串
4. **单位**：保持与后端配置一致，如温度使用 `℃`，湿度使用 `%`
5. **命令ID**：每次发布指令时应使用唯一的 commandId，避免重复处理

---

## 7. 常见错误排查

### 7.1 消息未被处理
- 检查主题格式是否正确
- 检查 Payload 格式是否符合 JSON 规范
- 查看后端日志，确认是否有解析错误

### 7.2 规则未触发
- 检查规则是否启用（在前端规则设置中确认）
- 检查数值是否确实超出阈值范围
- 检查是否启用了手动模式（手动模式下规则引擎不工作）

### 7.3 WebSocket 未更新
- 检查前端是否已连接到 WebSocket
- 确认主题订阅是否正确
- 查看后端日志，确认是否有推送错误
