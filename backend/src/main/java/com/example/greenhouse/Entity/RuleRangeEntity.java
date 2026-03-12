package com.example.greenhouse.Entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "rule_ranges", indexes = {
        @Index(name = "idx_rule_greenhouse_metric", columnList = "greenhouseId,metric")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RuleRangeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64)
    private String greenhouseId;

    @Column(nullable = false, length = 64)
    private String metric;

    @Column(nullable = false)
    private double minValue;

    @Column(nullable = false)
    private double maxValue;

    @Column(nullable = false)
    private boolean enabled;
}

