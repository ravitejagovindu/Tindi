package com.tindi.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class AuthDtos {
    private AuthDtos() {}

    static String trim(String s) { return s == null ? null : s.trim(); }

    public static final String EMAIL_REGEX = "^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$";

    public record SignupRequest(
            @NotBlank(message = "Enter your name") @Size(max = 60, message = "Use 60 characters or fewer") String name,
            @NotBlank(message = "Enter your email") @Size(max = 254, message = "Use 254 characters or fewer")
            @Pattern(regexp = EMAIL_REGEX, message = "Enter a valid email") String email,
            @NotBlank(message = "Enter a password") @Size(min = 6, max = 72, message = "Use 6 to 72 characters")
            String password) {
        public SignupRequest {
            name = trim(name);
            email = trim(email);
        }
    }

    public record LoginRequest(
            @NotBlank(message = "Enter your email") String email,
            @NotBlank(message = "Enter your password") String password) {
        public LoginRequest {
            email = trim(email);
        }
    }

    public record UserDto(String id, String name, String email) {}

    public record AuthResponse(String token, UserDto user) {}

    public record MeResponse(String id, String name, String email, long recipeCount, long categoryCount) {}
}
