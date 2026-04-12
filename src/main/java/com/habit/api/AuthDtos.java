package com.habit.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public final class AuthDtos {

    private AuthDtos() {}

    public record SignupRequest(
            @NotBlank @Email String email,
            @NotBlank @Size(max = 50) String nickname,
            @NotBlank @Size(min = 8, max = 100) String password
    ) {}

    public record SignupResponse(Long userId, UUID publicId, String email, String nickname) {}

    public record LoginRequest(@NotBlank @Email String email, @NotBlank String password) {}

    public record LoginResponse(String accessToken, String tokenType, long expiresIn) {}
}
