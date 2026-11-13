package com.smartkneeoa.backend.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "sensor_readings",
       indexes = {
           @Index(columnList = "assessment_id"),
           @Index(columnList = "recorded_at")
       })
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class SensorReading {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "assessment_id", nullable = false)
    private Assessment assessment;

    @Column(name = "sequence_number")
    private Integer sequenceNumber;

    // MPU6050 Accelerometer
    @Column(name = "acceleration_x")
    private Double accelerationX;
    @Column(name = "acceleration_y")
    private Double accelerationY;
    @Column(name = "acceleration_z")
    private Double accelerationZ;

    // MPU6050 Gyroscope
    @Column(name = "gyroscope_x")
    private Double gyroscopeX;
    @Column(name = "gyroscope_y")
    private Double gyroscopeY;
    @Column(name = "gyroscope_z")
    private Double gyroscopeZ;

    // Flex sensor (ADC 0-4095)
    @Column(name = "flex_value")
    private Integer flexValue;

    // FSR sensor (ADC 0-4095)
    @Column(name = "fsr_value")
    private Integer fsrValue;

    // Digital vibration
    @Column(name = "vibration_state")
    private Boolean vibrationState;

    // Availability flags
    @Column(name = "mpu_available", nullable = false)
    @Builder.Default
    private boolean mpuAvailable = true;

    @Column(name = "flex_available", nullable = false)
    @Builder.Default
    private boolean flexAvailable = true;

    @Column(name = "fsr_available", nullable = false)
    @Builder.Default
    private boolean fsrAvailable = true;

    @Column(name = "vib_available", nullable = false)
    @Builder.Default
    private boolean vibAvailable = true;

    @Column(name = "recorded_at", nullable = false)
    @Builder.Default
    private LocalDateTime recordedAt = LocalDateTime.now();
}
