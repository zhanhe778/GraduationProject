package com.example.greenhouse.Mapper;

import com.example.greenhouse.Entity.SensorEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SensorMapper extends JpaRepository<SensorEntity, Long> {

    Optional<SensorEntity> findByGreenhouseIdAndSensorId(String greenhouseId, String sensorId);

    List<SensorEntity> findByGreenhouseId(String greenhouseId);
}

