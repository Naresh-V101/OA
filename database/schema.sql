-- ============================================================
--  Smart Knee OA Assessment System – MySQL Schema
--  Database : smart_knee_oa
--  Charset  : utf8mb4 / utf8mb4_unicode_ci
-- ============================================================

CREATE DATABASE IF NOT EXISTS smart_knee_oa
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

USE smart_knee_oa;

-- ----------------------------------------------------------------
-- Table: users
-- ----------------------------------------------------------------
CREATE TABLE IF NOT EXISTS users (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    name          VARCHAR(120) NOT NULL,
    email         VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    age           INT          NULL CHECK (age > 0 AND age < 130),
    gender        ENUM('MALE','FEMALE','OTHER') NULL,
    height_cm     DECIMAL(5,2) NULL COMMENT 'Height in centimetres',
    weight_kg     DECIMAL(5,2) NULL COMMENT 'Weight in kilograms',
    role          ENUM('USER','ADMIN') NOT NULL DEFAULT 'USER',
    active        TINYINT(1)   NOT NULL DEFAULT 1,
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (id),
    INDEX idx_users_email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ----------------------------------------------------------------
-- Table: assessments
-- ----------------------------------------------------------------
CREATE TABLE IF NOT EXISTS assessments (
    id              BIGINT   NOT NULL AUTO_INCREMENT,
    user_id         BIGINT   NOT NULL,
    assessment_date DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    knee_side       ENUM('LEFT','RIGHT','BOTH') NOT NULL DEFAULT 'RIGHT',
    pain_score      TINYINT  NULL COMMENT '0-10 numeric pain scale',
    symptoms        TEXT     NULL COMMENT 'Free-text symptom description',
    status          ENUM('PENDING','IN_PROGRESS','COMPLETED','FAILED') NOT NULL DEFAULT 'PENDING',
    notes           TEXT     NULL,
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (id),
    CONSTRAINT fk_assessments_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    INDEX idx_assessments_user_id (user_id),
    INDEX idx_assessments_date (assessment_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ----------------------------------------------------------------
-- Table: sensor_readings
-- ----------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sensor_readings (
    id               BIGINT         NOT NULL AUTO_INCREMENT,
    assessment_id    BIGINT         NOT NULL,
    sequence_number  INT            NULL COMMENT 'BLE packet sequence number',
    -- MPU6050 Accelerometer (m/s²)
    acceleration_x   DECIMAL(10,6)  NULL,
    acceleration_y   DECIMAL(10,6)  NULL,
    acceleration_z   DECIMAL(10,6)  NULL,
    -- MPU6050 Gyroscope (°/s)
    gyroscope_x      DECIMAL(10,6)  NULL,
    gyroscope_y      DECIMAL(10,6)  NULL,
    gyroscope_z      DECIMAL(10,6)  NULL,
    -- Flex sensor (raw ADC 12-bit: 0-4095)
    flex_value       INT            NULL,
    -- FSR sensor (raw ADC 12-bit: 0-4095)
    fsr_value        INT            NULL,
    -- Digital vibration sensor
    vibration_state  TINYINT(1)     NULL COMMENT '1=vibration detected, 0=none',
    -- Availability flags (sensor may be offline)
    mpu_available    TINYINT(1)     NOT NULL DEFAULT 1,
    flex_available   TINYINT(1)     NOT NULL DEFAULT 1,
    fsr_available    TINYINT(1)     NOT NULL DEFAULT 1,
    vib_available    TINYINT(1)     NOT NULL DEFAULT 1,
    recorded_at      DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),

    PRIMARY KEY (id),
    CONSTRAINT fk_readings_assessment FOREIGN KEY (assessment_id) REFERENCES assessments (id) ON DELETE CASCADE,
    INDEX idx_readings_assessment_id (assessment_id),
    INDEX idx_readings_recorded_at (recorded_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ----------------------------------------------------------------
-- Table: assessment_features
--   Stores the derived feature vector sent to the ML service
-- ----------------------------------------------------------------
CREATE TABLE IF NOT EXISTS assessment_features (
    id                      BIGINT        NOT NULL AUTO_INCREMENT,
    assessment_id           BIGINT        NOT NULL UNIQUE,
    accel_mean_x            DECIMAL(10,6) NULL,
    accel_mean_y            DECIMAL(10,6) NULL,
    accel_mean_z            DECIMAL(10,6) NULL,
    accel_std_x             DECIMAL(10,6) NULL,
    accel_std_y             DECIMAL(10,6) NULL,
    accel_std_z             DECIMAL(10,6) NULL,
    accel_magnitude_mean    DECIMAL(10,6) NULL,
    gyro_mean_x             DECIMAL(10,6) NULL,
    gyro_mean_y             DECIMAL(10,6) NULL,
    gyro_mean_z             DECIMAL(10,6) NULL,
    gyro_std_x              DECIMAL(10,6) NULL,
    gyro_std_y              DECIMAL(10,6) NULL,
    gyro_std_z              DECIMAL(10,6) NULL,
    gyro_magnitude_mean     DECIMAL(10,6) NULL,
    flex_mean               DECIMAL(10,6) NULL,
    flex_std                DECIMAL(10,6) NULL,
    fsr_mean                DECIMAL(10,6) NULL,
    fsr_std                 DECIMAL(10,6) NULL,
    vibration_event_count   INT           NULL,
    pain_score              TINYINT       NULL,
    age                     INT           NULL,
    reading_count           INT           NULL,
    created_at              DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (id),
    CONSTRAINT fk_features_assessment FOREIGN KEY (assessment_id) REFERENCES assessments (id) ON DELETE CASCADE,
    INDEX idx_features_assessment_id (assessment_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ----------------------------------------------------------------
-- Table: assessment_results
-- ----------------------------------------------------------------
CREATE TABLE IF NOT EXISTS assessment_results (
    id               BIGINT         NOT NULL AUTO_INCREMENT,
    assessment_id    BIGINT         NOT NULL UNIQUE,
    risk_category    ENUM('LOW','MODERATE','HIGH') NOT NULL,
    prob_low         DECIMAL(6,4)   NULL COMMENT 'Class probability – Low',
    prob_moderate    DECIMAL(6,4)   NULL COMMENT 'Class probability – Moderate',
    prob_high        DECIMAL(6,4)   NULL COMMENT 'Class probability – High',
    prediction_score DECIMAL(6,4)   NULL COMMENT 'Highest class probability',
    model_version    VARCHAR(50)    NOT NULL DEFAULT 'unknown',
    is_demo          TINYINT(1)     NOT NULL DEFAULT 1 COMMENT 'TRUE = synthetic demo, not clinical',
    created_at       DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (id),
    CONSTRAINT fk_results_assessment FOREIGN KEY (assessment_id) REFERENCES assessments (id) ON DELETE CASCADE,
    INDEX idx_results_assessment_id (assessment_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ----------------------------------------------------------------
-- Table: reports
-- ----------------------------------------------------------------
CREATE TABLE IF NOT EXISTS reports (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    user_id       BIGINT       NOT NULL,
    assessment_id BIGINT       NULL,
    report_type   ENUM('SINGLE','HISTORY','SUMMARY') NOT NULL DEFAULT 'SINGLE',
    file_path     VARCHAR(512) NULL,
    generated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (id),
    CONSTRAINT fk_reports_user       FOREIGN KEY (user_id)       REFERENCES users       (id) ON DELETE CASCADE,
    CONSTRAINT fk_reports_assessment FOREIGN KEY (assessment_id) REFERENCES assessments (id) ON DELETE SET NULL,
    INDEX idx_reports_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ----------------------------------------------------------------
-- Seed: default admin user  (password: Admin@123 – BCrypt)
-- ----------------------------------------------------------------
INSERT INTO users (name, email, password_hash, age, gender, role)
VALUES (
    'System Admin',
    'admin@smartkneeoa.local',
    '$2a$12$9oGzQXy1fVtKlNQ8xqc4hOU4SjHWZ0qXRSPjMPsBh5F3tH6RR4kW.',
    30,
    'OTHER',
    'ADMIN'
) ON DUPLICATE KEY UPDATE id = id;
