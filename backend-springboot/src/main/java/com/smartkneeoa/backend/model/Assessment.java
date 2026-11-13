package com.smartkneeoa.backend.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "assessments",
       indexes = {
           @Index(columnList = "user_id"),
           @Index(columnList = "assessment_date")
       })
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Assessment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "assessment_date", nullable = false)
    @Builder.Default
    private LocalDateTime assessmentDate = LocalDateTime.now();

    @Enumerated(EnumType.STRING)
    @Column(name = "knee_side", nullable = false)
    @Builder.Default
    private KneeSide kneeSide = KneeSide.RIGHT;

    @Min(0) @Max(10)
    @Column(name = "pain_score")
    private Integer painScore;

    @Column(columnDefinition = "TEXT")
    private String symptoms;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private Status status = Status.PENDING;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "assessment", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<SensorReading> sensorReadings;

    @OneToOne(mappedBy = "assessment", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private AssessmentFeatures features;

    @OneToOne(mappedBy = "assessment", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private AssessmentResult result;

    public enum KneeSide { LEFT, RIGHT, BOTH }
    public enum Status   { PENDING, IN_PROGRESS, COMPLETED, FAILED }
}
