package com.example.greenhouse.service;

import com.example.greenhouse.Entity.ManualModeEntity;
import com.example.greenhouse.Mapper.ManualModeMapper;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ManualModeService {

    private final ManualModeMapper manualModeMapper;

    @Transactional(readOnly = true)
    public boolean isManualModeEnabled(String greenhouseId) {
        return manualModeMapper.findByGreenhouseId(greenhouseId)
                .map(ManualModeEntity::isEnabled)
                .orElse(false);
    }

    @Transactional
    public ManualModeEntity setManualMode(String greenhouseId, boolean enabled) {
        ManualModeEntity mode = manualModeMapper.findByGreenhouseId(greenhouseId)
                .orElseGet(() -> ManualModeEntity.builder()
                        .greenhouseId(greenhouseId)
                        .build());
        mode.setEnabled(enabled);
        mode.setUpdatedAt(Instant.now());
        return manualModeMapper.save(mode);
    }
}
