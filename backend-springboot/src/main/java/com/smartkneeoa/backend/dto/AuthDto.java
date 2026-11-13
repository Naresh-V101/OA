package com.smartkneeoa.backend.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

/** Request DTOs for Authentication **/
public class AuthDto {

    @Data
    public static class RegisterRequest {
        @NotBlank @Size(max = 120)
        private String name;

        @NotBlank @Email @Size(max = 255)
        private String email;

        @NotBlank @Size(min = 8, max = 72)
        private String password;

        @Min(1) @Max(129)
        private Integer age;

        private String gender;   // MALE | FEMALE | OTHER
        private Double heightCm;
        private Double weightKg;
    }

    @Data
    public static class LoginRequest {
        @NotBlank @Email
        private String email;

        @NotBlank
        private String password;
    }

    @Data
    public static class AuthResponse {
        private String token;
        private String tokenType = "Bearer";
        private Long userId;
        private String name;
        private String email;
        private String role;

        public AuthResponse(String token, Long userId, String name, String email, String role) {
            this.token  = token;
            this.userId = userId;
            this.name   = name;
            this.email  = email;
            this.role   = role;
        }
    }
}
