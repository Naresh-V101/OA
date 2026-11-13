package com.smartkneeoa.backend.repository;

import com.smartkneeoa.backend.model.AssessmentFeatures;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AssessmentFeaturesRepository extends JpaRepository<AssessmentFeatures, Long> {
    Optional<AssessmentFeatures> findByAssessmentId(Long assessmentId);
}
