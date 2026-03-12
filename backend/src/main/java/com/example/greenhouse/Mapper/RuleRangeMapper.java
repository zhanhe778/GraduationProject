package com.example.greenhouse.Mapper;

import com.example.greenhouse.Entity.RuleRangeEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RuleRangeMapper extends JpaRepository<RuleRangeEntity, Long> {

    List<RuleRangeEntity> findByGreenhouseId(String greenhouseId);

    Optional<RuleRangeEntity> findByGreenhouseIdAndMetric(String greenhouseId, String metric);
}

