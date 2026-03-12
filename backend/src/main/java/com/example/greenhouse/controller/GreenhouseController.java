package com.example.greenhouse.controller;

import com.example.greenhouse.Entity.RuleRangeEntity;
import com.example.greenhouse.Mapper.RuleRangeMapper;
import com.example.greenhouse.Mapper.SensorDataMapper;
import com.example.greenhouse.dto.GreenhouseDetailDto;
import com.example.greenhouse.dto.GreenhouseSummaryDto;
import com.example.greenhouse.dto.ManualCommandRequest;
import com.example.greenhouse.dto.ManualModeRequest;
import com.example.greenhouse.dto.RuleRangeBatchRequest;
import com.example.greenhouse.dto.RuleRangeDto;
import com.example.greenhouse.dto.SensorHistoryDto;
import com.example.greenhouse.service.RuleEngineService;
import com.example.greenhouse.service.GreenhouseQueryService;
import com.example.greenhouse.service.ManualControlService;
import com.example.greenhouse.service.ManualModeService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/greenhouses")
@RequiredArgsConstructor
public class GreenhouseController {

    private final GreenhouseQueryService greenhouseQueryService;
    private final ManualModeService manualModeService;
    private final ManualControlService manualControlService;
    private final RuleEngineService ruleEngineService;
    private final RuleRangeMapper ruleRangeMapper;
    private final SensorDataMapper sensorDataMapper;

    /**
     * 获取大棚列表
     * 返回所有大棚的摘要信息
     */
    @GetMapping
    public ResponseEntity<List<GreenhouseSummaryDto>> list() {
        return ResponseEntity.ok(greenhouseQueryService.listGreenhouses());
    }

    /**
     * 获取特定大棚的详细信息
     * 包括传感器列表、执行器状态等
     */
    @GetMapping("/{greenhouseId}")
    public ResponseEntity<GreenhouseDetailDto> detail(@PathVariable String greenhouseId) {
        return ResponseEntity.ok(greenhouseQueryService.getDetail(greenhouseId));
    }

    /**
     * 切换大棚的手动/自动模式
     * 启用手动模式时，规则引擎将停止自动控制
     */
    @PostMapping("/{greenhouseId}/manual-mode")
    public ResponseEntity<Void> updateManualMode(
            @PathVariable String greenhouseId,
            @Valid @RequestBody ManualModeRequest request
    ) {
        manualModeService.setManualMode(greenhouseId, request.getEnabled());
        return ResponseEntity.ok().build();
    }

    /**
     * 发送手动控制指令
     * 只有在手动模式下才允许调用（或强制覆盖）
     */
    @PostMapping("/{greenhouseId}/actuators/{actuatorId}/manual-command")
    public ResponseEntity<Void> manualCommand(
            @PathVariable String greenhouseId,
            @PathVariable String actuatorId,
            @Valid @RequestBody ManualCommandRequest request
    ) {
        manualControlService.sendManualCommand(greenhouseId, actuatorId, request.getAction(), request.getReason());
        return ResponseEntity.ok().build();
    }

    /**
     * 获取大棚的规则配置列表
     * 例如：温度、湿度、光照的上下限阈值
     */
    @GetMapping("/{greenhouseId}/rules")
    public ResponseEntity<List<RuleRangeDto>> listRules(@PathVariable String greenhouseId) {
        List<RuleRangeDto> rules = ruleRangeMapper.findByGreenhouseId(greenhouseId).stream()
                .map(rule -> RuleRangeDto.builder()
                        .metric(rule.getMetric())
                        .minValue(rule.getMinValue())
                        .maxValue(rule.getMaxValue())
                        .enabled(rule.isEnabled())
                        .build())
                .collect(Collectors.toList());
        return ResponseEntity.ok(rules);
    }

    /**
     * 批量更新大棚的规则配置
     */
    @PostMapping("/{greenhouseId}/rules/batch")
    public ResponseEntity<Void> upsertRules(
            @PathVariable String greenhouseId,
            @Valid @RequestBody RuleRangeBatchRequest request
    ) {
        for (RuleRangeDto dto : request.getRules()) {
            RuleRangeEntity entity = ruleRangeMapper.findByGreenhouseIdAndMetric(greenhouseId, dto.getMetric())
                    .orElseGet(() -> RuleRangeEntity.builder()
                            .greenhouseId(greenhouseId)
                            .metric(dto.getMetric())
                            .build());
            entity.setMinValue(dto.getMinValue());
            entity.setMaxValue(dto.getMaxValue());
            entity.setEnabled(dto.isEnabled());
            ruleRangeMapper.save(entity);
        }
        
        // 规则更新后，立即对当前最新的传感器数据进行一次重新评估
        // 确保执行器状态能立即反映最新的规则
        ruleEngineService.reevaluateAll(greenhouseId);
        
        return ResponseEntity.ok().build();
    }

    /**
     * 分页查询传感器历史数据
     * 默认每页 10 条，按时间倒序排列
     */
    @GetMapping("/{greenhouseId}/sensors/{sensorId}/history")
    public ResponseEntity<Page<SensorHistoryDto>> history(
            @PathVariable String greenhouseId,
            @PathVariable String sensorId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        Page<SensorHistoryDto> history = sensorDataMapper
                .findByGreenhouseIdAndSensorIdOrderByTimestampDesc(
                        greenhouseId, sensorId, PageRequest.of(page, size))
                .map(data -> SensorHistoryDto.builder()
                        .sensorId(data.getSensorId())
                        .type(data.getType())
                        .value(data.getValue())
                        .unit(data.getUnit())
                        .timestamp(data.getTimestamp())
                        .build());
        return ResponseEntity.ok(history);
    }
}
