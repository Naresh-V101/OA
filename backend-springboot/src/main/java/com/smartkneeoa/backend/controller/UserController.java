package com.smartkneeoa.backend.controller;

import com.smartkneeoa.backend.model.User;
import com.smartkneeoa.backend.repository.UserRepository;
import com.smartkneeoa.backend.service.AuthService;
import jakarta.validation.Valid;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final AuthService    authService;
    private final UserRepository userRepository;

    @GetMapping("/profile")
    public ResponseEntity<UserProfileResponse> getProfile() {
        User user = authService.getCurrentUser();
        return ResponseEntity.ok(toResponse(user));
    }

    @PutMapping("/profile")
    public ResponseEntity<UserProfileResponse> updateProfile(
            @Valid @RequestBody UpdateProfileRequest req) {
        User user = authService.getCurrentUser();
        if (req.getName()     != null) user.setName(req.getName());
        if (req.getAge()      != null) user.setAge(req.getAge());
        if (req.getGender()   != null) user.setGender(User.Gender.valueOf(req.getGender().toUpperCase()));
        if (req.getHeightCm() != null) user.setHeightCm(req.getHeightCm());
        if (req.getWeightKg() != null) user.setWeightKg(req.getWeightKg());
        userRepository.save(user);
        return ResponseEntity.ok(toResponse(user));
    }

    @GetMapping("/dashboard-stats")
    public ResponseEntity<Map<String, Object>> dashboardStats() {
        User user = authService.getCurrentUser();
        long count = userRepository.count(); // total assessments handled in AssessmentController
        return ResponseEntity.ok(Map.of(
                "userId", user.getId(),
                "name", user.getName()
        ));
    }

    private UserProfileResponse toResponse(User u) {
        UserProfileResponse r = new UserProfileResponse();
        r.setId(u.getId());
        r.setName(u.getName());
        r.setEmail(u.getEmail());
        r.setAge(u.getAge());
        r.setGender(u.getGender() != null ? u.getGender().name() : null);
        r.setHeightCm(u.getHeightCm());
        r.setWeightKg(u.getWeightKg());
        r.setRole(u.getRole().name());
        r.setCreatedAt(u.getCreatedAt() != null ? u.getCreatedAt().toString() : null);
        return r;
    }

    @Data
    static class UserProfileResponse {
        private Long id;
        private String name, email, gender, role, createdAt;
        private Integer age;
        private Double heightCm, weightKg;
    }

    @Data
    static class UpdateProfileRequest {
        private String name, gender;
        private Integer age;
        private Double heightCm, weightKg;
    }
}
