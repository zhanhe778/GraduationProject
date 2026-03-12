package com.example.greenhouse.Mapper;

import com.example.greenhouse.Entity.ActuatorEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ActuatorMapper extends JpaRepository<ActuatorEntity, Long> {

    Optional<ActuatorEntity> findByGreenhouseIdAndActuatorId(String greenhouseId, String actuatorId);

    List<ActuatorEntity> findByGreenhouseId(String greenhouseId);

    List<ActuatorEntity> findByGreenhouseIdAndType(String greenhouseId, String type);
}

