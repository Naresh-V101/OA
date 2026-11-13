package com.smartkneeoa.backend.repository;

import com.smartkneeoa.backend.model.Assessment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface AssessmentRepository extends JpaRepository<Assessment, Long> {

    Page<Assessment> findByUserIdOrderByAssessmentDateDesc(Long userId, Pageable pageable);

    List<Assessment> findByUserIdOrderByAssessmentDateDesc(Long userId);

    @Query("SELECT a FROM Assessment a WHERE a.user.id = :userId " +
           "AND a.assessmentDate BETWEEN :from AND :to " +
           "ORDER BY a.assessmentDate DESC")
    List<Assessment> findByUserIdAndDateRange(Long userId, LocalDateTime from, LocalDateTime to);

    Optional<Assessment> findByIdAndUserId(Long id, Long userId);

    long countByUserId(Long userId);
}
