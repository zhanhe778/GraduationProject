package com.example.greenhouse.Mapper;

import com.example.greenhouse.Entity.ActuatorCommandEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ActuatorCommandMapper extends JpaRepository<ActuatorCommandEntity, Long> {

    List<ActuatorCommandEntity> findByGreenhouseIdOrderByTimestampDesc(String greenhouseId);

    Optional<ActuatorCommandEntity> findByCommandId(String commandId);
}
