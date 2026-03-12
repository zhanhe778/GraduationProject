package com.example.greenhouse.Mapper;

import com.example.greenhouse.Entity.ManualModeEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ManualModeMapper extends JpaRepository<ManualModeEntity, Long> {

    Optional<ManualModeEntity> findByGreenhouseId(String greenhouseId);
}

