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
@Table(name = "sensor_data", indexes = {
        @Index(name = "idx_data_greenhouse", columnList = "greenhouseId"),
        @Index(name = "idx_data_sensor_time", columnList = "greenhouseId,sensorId,timestamp")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SensorDataEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64)
    private String greenhouseId;

    @Column(nullable = false, length = 64)
    private String sensorId;

    @Column(nullable = false, length = 64)
    private String type;

    @Column(name = "reading_value", nullable = false, columnDefinition = "double")
    private double value;

    @Column(length = 32)
    private String unit;

    @Column(name = "reading_time", nullable = false, columnDefinition = "datetime")
    private Instant timestamp;
}
