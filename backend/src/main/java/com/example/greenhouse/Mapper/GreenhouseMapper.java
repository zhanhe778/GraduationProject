package com.example.greenhouse.Mapper;

import com.example.greenhouse.Entity.GreenhouseEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GreenhouseMapper extends JpaRepository<GreenhouseEntity, Long> {

    Optional<GreenhouseEntity> findByGreenhouseId(String greenhouseId);
}

