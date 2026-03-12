package com.example.greenhouse.Mapper;

import com.example.greenhouse.Entity.SensorDataEntity;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SensorDataMapper extends JpaRepository<SensorDataEntity, Long> {

    List<SensorDataEntity> findByGreenhouseIdAndSensorIdAndTimestampBetweenOrderByTimestampAsc(
            String greenhouseId, String sensorId, Instant start, Instant end);

    Page<SensorDataEntity> findByGreenhouseIdAndSensorIdOrderByTimestampDesc(
            String greenhouseId, String sensorId, Pageable pageable);
}

