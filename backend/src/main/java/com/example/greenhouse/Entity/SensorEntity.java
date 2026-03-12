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
@Table(name = "sensors", indexes = {
        @Index(name = "idx_sensor_greenhouse", columnList = "greenhouseId"),
        @Index(name = "idx_sensor_unique", columnList = "greenhouseId,sensorId")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SensorEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64)
    private String greenhouseId;

    @Column(nullable = false, length = 64)
    private String sensorId;

    @Column(nullable = false, length = 64)
    private String type;

    @Column(length = 32)
    private String unit;

    @Column(name = "last_val", columnDefinition = "double")
    private Double lastValue;

    @Column(name = "last_ts", columnDefinition = "datetime")
    private Instant lastTimestamp;
}
