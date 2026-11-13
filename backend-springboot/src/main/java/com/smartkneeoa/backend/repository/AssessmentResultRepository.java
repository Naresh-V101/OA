package com.smartkneeoa.backend.repository;

import com.smartkneeoa.backend.model.AssessmentResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AssessmentResultRepository extends JpaRepository<AssessmentResult, Long> {
    Optional<AssessmentResult> findByAssessmentId(Long assessmentId);
}
