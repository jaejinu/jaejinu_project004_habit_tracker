package com.habit.api;

import com.habit.domain.auth.AuthService;
import com.habit.domain.user.User;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Auth", description = "회원가입 / 로그인")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/signup")
    @SecurityRequirements({})
    @Operation(summary = "회원가입", description = "이메일·닉네임·비밀번호로 가입합니다.")
    @ApiResponse(responseCode = "409", description = "이미 사용 중인 이메일")
    public AuthDtos.SignupResponse signup(@RequestBody @Valid AuthDtos.SignupRequest req) {
        User user = authService.signup(req.email(), req.nickname(), req.password());
        return new AuthDtos.SignupResponse(user.getId(), user.getPublicId(), user.getEmail(), user.getNickname());
    }

    @PostMapping("/login")
    @SecurityRequirements({})
    @Operation(summary = "로그인", description = "JWT 액세스 토큰을 발급합니다.")
    @ApiResponse(responseCode = "401", description = "이메일 또는 비밀번호 불일치")
    public AuthDtos.LoginResponse login(@RequestBody @Valid AuthDtos.LoginRequest req) {
        return authService.login(req.email(), req.password());
    }
}
