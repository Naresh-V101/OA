package com.smartkneeoa.backend.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "assessment_features")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class AssessmentFeatures {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "assessment_id", nullable = false, unique = true)
    private Assessment assessment;

    private Double accelMeanX;
    private Double accelMeanY;
    private Double accelMeanZ;
    private Double accelStdX;
    private Double accelStdY;
    private Double accelStdZ;
    private Double accelMagnitudeMean;

    private Double gyroMeanX;
    private Double gyroMeanY;
    private Double gyroMeanZ;
    private Double gyroStdX;
    private Double gyroStdY;
    private Double gyroStdZ;
    private Double gyroMagnitudeMean;

    private Double flexMean;
    private Double flexStd;
    private Double fsrMean;
    private Double fsrStd;
    private Integer vibrationEventCount;

    private Integer painScore;
    private Integer age;
    private Integer readingCount;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
