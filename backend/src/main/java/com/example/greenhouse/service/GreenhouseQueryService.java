package com.example.greenhouse.service;

import com.example.greenhouse.Entity.ActuatorEntity;
import com.example.greenhouse.Entity.GreenhouseEntity;
import com.example.greenhouse.Entity.RuleRangeEntity;
import com.example.greenhouse.Entity.SensorEntity;
import com.example.greenhouse.Mapper.ActuatorMapper;
import com.example.greenhouse.Mapper.GreenhouseMapper;
import com.example.greenhouse.Mapper.RuleRangeMapper;
import com.example.greenhouse.Mapper.SensorMapper;
import com.example.greenhouse.dto.ActuatorStatusDto;
import com.example.greenhouse.dto.GreenhouseDetailDto;
import com.example.greenhouse.dto.GreenhouseSummaryDto;
import com.example.greenhouse.dto.RuleRangeDto;
import com.example.greenhouse.dto.SensorStatusDto;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GreenhouseQueryService {

    private final GreenhouseMapper greenhouseMapper;
    private final SensorMapper sensorMapper;
    private final ActuatorMapper actuatorMapper;
    private final RuleRangeMapper ruleRangeMapper;
    private final ManualModeService manualModeService;

    @Transactional(readOnly = true)
    public List<GreenhouseSummaryDto> listGreenhouses() {
        return greenhouseMapper.findAll().stream()
                .sorted(Comparator.comparing(GreenhouseEntity::getGreenhouseId))
                .map(this::toSummary)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public GreenhouseDetailDto getDetail(String greenhouseId) {
        GreenhouseEntity greenhouse = greenhouseMapper.findByGreenhouseId(greenhouseId)
                .orElseThrow(() -> new IllegalArgumentException("Greenhouse not found"));
        List<SensorStatusDto> sensors = sensorMapper.findByGreenhouseId(greenhouseId).stream()
                .map(this::toSensorStatus)
                .collect(Collectors.toList());
        List<ActuatorStatusDto> actuators = actuatorMapper.findByGreenhouseId(greenhouseId).stream()
                .map(this::toActuatorStatus)
                .collect(Collectors.toList());
        List<RuleRangeDto> rules = ruleRangeMapper.findByGreenhouseId(greenhouseId).stream()
                .map(this::toRuleRange)
                .collect(Collectors.toList());
        return GreenhouseDetailDto.builder()
                .greenhouseId(greenhouse.getGreenhouseId())
                .name(greenhouse.getName())
                .manualMode(manualModeService.isManualModeEnabled(greenhouseId))
                .sensors(sensors)
                .actuators(actuators)
                .rules(rules)
                .build();
    }

    private GreenhouseSummaryDto toSummary(GreenhouseEntity greenhouse) {
        String greenhouseId = greenhouse.getGreenhouseId();
        List<SensorStatusDto> sensors = sensorMapper.findByGreenhouseId(greenhouseId).stream()
                .map(this::toSensorStatus)
                .collect(Collectors.toList());
        List<ActuatorStatusDto> actuators = actuatorMapper.findByGreenhouseId(greenhouseId).stream()
                .map(this::toActuatorStatus)
                .collect(Collectors.toList());
        return GreenhouseSummaryDto.builder()
                .greenhouseId(greenhouseId)
                .name(greenhouse.getName())
                .manualMode(manualModeService.isManualModeEnabled(greenhouseId))
                .sensors(sensors)
                .actuators(actuators)
                .build();
    }

    private SensorStatusDto toSensorStatus(SensorEntity sensor) {
        return SensorStatusDto.builder()
                .greenhouseId(sensor.getGreenhouseId())
                .sensorId(sensor.getSensorId())
                .type(sensor.getType())
                .value(sensor.getLastValue())
                .unit(sensor.getUnit())
                .timestamp(sensor.getLastTimestamp())
                .build();
    }

    private ActuatorStatusDto toActuatorStatus(ActuatorEntity actuator) {
        return ActuatorStatusDto.builder()
                .greenhouseId(actuator.getGreenhouseId())
                .actuatorId(actuator.getActuatorId())
                .type(actuator.getType())
                .status(actuator.getStatus())
                .lastCommandAt(actuator.getLastCommandAt())
                .build();
    }

    private RuleRangeDto toRuleRange(RuleRangeEntity rule) {
        return RuleRangeDto.builder()
                .metric(rule.getMetric())
                .minValue(rule.getMinValue())
                .maxValue(rule.getMaxValue())
                .enabled(rule.isEnabled())
                .build();
    }
}
