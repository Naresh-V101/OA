package com.smartkneeoa.backend.controller;

import com.smartkneeoa.backend.dto.AssessmentDto;
import com.smartkneeoa.backend.model.*;
import com.smartkneeoa.backend.service.AssessmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/assessments")
@RequiredArgsConstructor
public class AssessmentController {

    private final AssessmentService assessmentService;

    /** POST /api/assessments — create a new assessment */
    @PostMapping
    public ResponseEntity<AssessmentDto.AssessmentResponse> create(
            @Valid @RequestBody AssessmentDto.CreateRequest req) {
        Assessment assessment = assessmentService.createAssessment(req);
        return ResponseEntity.ok(toResponse(assessment));
    }

    /** GET /api/assessments — list all assessments for authenticated user */
    @GetMapping
    public ResponseEntity<List<AssessmentDto.AssessmentResponse>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        List<Assessment> assessments =
                assessmentService.getAssessmentsForCurrentUser(page, size);
        return ResponseEntity.ok(
                assessments.stream().map(this::toResponse).collect(Collectors.toList()));
    }

    /** GET /api/assessments/{id} */
    @GetMapping("/{id}")
    public ResponseEntity<AssessmentDto.AssessmentResponse> getById(@PathVariable Long id) {
        Assessment a = assessmentService.getAssessmentForUser(
                id, assessmentService.getCurrentUserId());
        return ResponseEntity.ok(toResponse(a));
    }

    /** POST /api/assessments/{id}/sensor-data — add one sensor reading */
    @PostMapping("/{id}/sensor-data")
    public ResponseEntity<String> addSensorData(
            @PathVariable Long id,
            @RequestBody AssessmentDto.SensorDataRequest req) {
        assessmentService.addSensorReading(id, req);
        return ResponseEntity.ok("{\"message\":\"Sensor reading recorded\"}");
    }

    /** POST /api/assessments/{id}/predict — trigger ML prediction */
    @PostMapping("/{id}/predict")
    public ResponseEntity<AssessmentDto.AssessmentResponse> predict(@PathVariable Long id) {
        assessmentService.predict(id);
        Assessment a = assessmentService.getAssessmentForUser(
                id, assessmentService.getCurrentUserId());
        return ResponseEntity.ok(toResponse(a));
    }

    /** GET /api/assessments/{id}/result — get prediction result */
    @GetMapping("/{id}/result")
    public ResponseEntity<AssessmentDto.ResultSummary> result(@PathVariable Long id) {
        AssessmentResult r = assessmentService.getResult(id);
        return ResponseEntity.ok(toResultSummary(r));
    }

    // ---- Mapping helpers ----

    private AssessmentDto.AssessmentResponse toResponse(Assessment a) {
        AssessmentDto.AssessmentResponse dto = new AssessmentDto.AssessmentResponse();
        dto.setId(a.getId());
        dto.setUserId(a.getUser().getId());
        dto.setAssessmentDate(a.getAssessmentDate());
        dto.setKneeSide(a.getKneeSide().name());
        dto.setPainScore(a.getPainScore());
        dto.setSymptoms(a.getSymptoms());
        dto.setStatus(a.getStatus().name());
        dto.setNotes(a.getNotes());
        if (a.getResult() != null) {
            dto.setResult(toResultSummary(a.getResult()));
        }
        return dto;
    }

    private AssessmentDto.ResultSummary toResultSummary(AssessmentResult r) {
        AssessmentDto.ResultSummary s = new AssessmentDto.ResultSummary();
        s.setRiskCategory(r.getRiskCategory().name());
        s.setPredictionScore(r.getPredictionScore());
        s.setProbLow(r.getProbLow());
        s.setProbModerate(r.getProbModerate());
        s.setProbHigh(r.getProbHigh());
        s.setModelVersion(r.getModelVersion());
        s.setDemo(r.isDemo());
        s.setCreatedAt(r.getCreatedAt());
        return s;
    }
}
