package com.smartkneeoa.backend.repository;

import com.smartkneeoa.backend.model.SensorReading;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SensorReadingRepository extends JpaRepository<SensorReading, Long> {

    List<SensorReading> findByAssessmentIdOrderByRecordedAtAsc(Long assessmentId);

    long countByAssessmentId(Long assessmentId);

    @Query("SELECT COUNT(r) FROM SensorReading r WHERE r.assessment.id = :assessmentId AND r.vibrationState = true")
    long countVibrationEvents(Long assessmentId);
}
