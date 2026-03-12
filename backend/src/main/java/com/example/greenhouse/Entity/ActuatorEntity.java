package com.example.greenhouse.Entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "actuators", indexes = {
        @Index(name = "idx_actuator_greenhouse", columnList = "greenhouseId"),
        @Index(name = "idx_actuator_unique", columnList = "greenhouseId,actuatorId")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ActuatorEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64)
    private String greenhouseId;

    @Column(nullable = false, length = 64)
    private String actuatorId;

    @Column(nullable = false, length = 64)
    private String type;

    @Column(length = 16)
    private String status;

    @Column
    private Instant lastCommandAt;
}

