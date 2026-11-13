package com.smartkneeoa.backend.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "assessment_results")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class AssessmentResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "assessment_id", nullable = false, unique = true)
    private Assessment assessment;

    @Enumerated(EnumType.STRING)
    @Column(name = "risk_category", nullable = false)
    private RiskCategory riskCategory;

    @Column(name = "prob_low")
    private Double probLow;

    @Column(name = "prob_moderate")
    private Double probModerate;

    @Column(name = "prob_high")
    private Double probHigh;

    @Column(name = "prediction_score")
    private Double predictionScore;

    @Column(name = "model_version", nullable = false)
    @Builder.Default
    private String modelVersion = "unknown";

    @Column(name = "is_demo", nullable = false)
    @Builder.Default
    private boolean isDemo = true;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    public enum RiskCategory { LOW, MODERATE, HIGH }
}
