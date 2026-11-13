package com.smartkneeoa.backend.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.time.LocalDateTime;

public class AssessmentDto {

    @Data
    public static class CreateRequest {
        @NotNull
        private String kneeSide;  // LEFT | RIGHT | BOTH

        @Min(0) @Max(10)
        private Integer painScore;

        private String symptoms;
        private String notes;
    }

    @Data
    public static class SensorDataRequest {
        private Integer sequenceNumber;

        private Double accelerationX;
        private Double accelerationY;
        private Double accelerationZ;
        private Double gyroscopeX;
        private Double gyroscopeY;
        private Double gyroscopeZ;
        private Integer flexValue;
        private Integer fsrValue;
        private Boolean vibrationState;

        private boolean mpuAvailable  = true;
        private boolean flexAvailable = true;
        private boolean fsrAvailable  = true;
        private boolean vibAvailable  = true;
    }

    @Data
    public static class AssessmentResponse {
        private Long          id;
        private Long          userId;
        private LocalDateTime assessmentDate;
        private String        kneeSide;
        private Integer       painScore;
        private String        symptoms;
        private String        status;
        private String        notes;
        private Integer       readingCount;
        private ResultSummary result;
    }

    @Data
    public static class ResultSummary {
        private String  riskCategory;
        private Double  predictionScore;
        private Double  probLow;
        private Double  probModerate;
        private Double  probHigh;
        private String  modelVersion;
        private boolean isDemo;
        private LocalDateTime createdAt;
    }
}
