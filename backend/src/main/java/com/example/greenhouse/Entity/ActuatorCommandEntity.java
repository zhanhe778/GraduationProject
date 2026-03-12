package com.example.greenhouse.Entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
@Table(name = "actuator_commands", indexes = {
        @Index(name = "idx_command_greenhouse", columnList = "greenhouseId"),
        @Index(name = "idx_command_actuator_time", columnList = "greenhouseId,actuatorId,timestamp")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ActuatorCommandEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64, unique = true)
    private String commandId;

    @Column(nullable = false, length = 64)
    private String actuatorId;

    @Column(nullable = false, length = 64)
    private String greenhouseId;

    @Column(nullable = false, length = 64)
    private String type;

    @Column(nullable = false, length = 32)
    private String action;

    @Column(length = 255)
    private String reason;

    @Column(nullable = false)
    private Instant timestamp;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private Source source;

    public enum Source {
        AUTO,
        MANUAL
    }
}

