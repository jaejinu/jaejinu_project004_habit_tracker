package com.habit.domain.auth;

import com.habit.api.AuthDtos;
import com.habit.common.exception.BusinessException;
import com.habit.common.exception.ErrorCode;
import com.habit.domain.user.User;
import com.habit.domain.user.UserRepository;
import com.habit.infra.security.JwtService;

import lombok.RequiredArgsConstructor;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    @Transactional
    public User signup(String email, String nickname, String rawPassword) {
        if (userRepository.existsByEmail(email)) {
            throw new BusinessException(ErrorCode.USER_EMAIL_DUPLICATED);
        }
        String hash = passwordEncoder.encode(rawPassword);
        return userRepository.save(User.createWithPassword(email, nickname, hash));
    }

    public AuthDtos.LoginResponse login(String email, String rawPassword) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException(ErrorCode.AUTH_INVALID_CREDENTIALS));
        if (!passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.AUTH_INVALID_CREDENTIALS);
        }
        String token = jwtService.issueAccessToken(user.getId());
        return new AuthDtos.LoginResponse(token, "Bearer", jwtService.getAccessTokenTtl().toSeconds());
    }
}
