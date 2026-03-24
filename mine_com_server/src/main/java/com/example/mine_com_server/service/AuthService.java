package com.example.mine_com_server.service;

import com.example.mine_com_server.dto.request.LoginRequest;
import com.example.mine_com_server.dto.request.RegisterRequest;
import com.example.mine_com_server.dto.response.AuthResponse;
import com.example.mine_com_server.dto.response.UserResponse;
import com.example.mine_com_server.exception.NotFoundException;
import com.example.mine_com_server.model.User;
import com.example.mine_com_server.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final RefreshTokenService refreshTokenService;

    // ===== РЕГИСТРАЦИЯ =====

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalStateException("Email уже занят: " + request.getEmail());
        }
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new IllegalStateException("Имя пользователя занято: " + request.getUsername());
        }

        User user = User.builder()
                .username(request.getUsername())
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .phoneNumber(request.getPhoneNumber())
                .role("user")
                .isActive(true)
                .build();

        user = userRepository.save(user);
        log.info("[AUTH] Зарегистрирован: {}", user.getEmail());
        return buildAuthResponse(user);
    }

    // ===== ЛОГИН =====

    @Transactional
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new NotFoundException("Пользователь не найден"));

        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        user.getId().toString(),
                        request.getPassword()
                )
        );

        log.info("[AUTH] Вход: {}", user.getEmail());
        return buildAuthResponse(user);
    }

    // ===== REFRESH =====

    @Transactional
    public AuthResponse refresh(String refreshTokenValue) {
        UUID userId = refreshTokenService.validate(refreshTokenValue);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("Пользователь не найден"));
        return buildAuthResponse(user);
    }

    // ===== LOGOUT =====

    @Transactional
    public void logout(UUID userId) {
        refreshTokenService.revokeByUserId(userId);
        log.info("[AUTH] Выход: {}", userId);
    }

    // ===== ME =====

    public UserResponse getMe(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("Пользователь не найден"));
        return toUserResponse(user);
    }

    // ===== УТИЛИТЫ =====

    private AuthResponse buildAuthResponse(User user) {
        String accessToken  = jwtService.generateToken(user.getId());
        String refreshToken = refreshTokenService.create(user.getId());
        return new AuthResponse(
                accessToken,
                refreshToken,
                user.getUsername(),
                user.getEmail(),
                user.getRole()
        );
    }

    private UserResponse toUserResponse(User user) {
        UserResponse r = new UserResponse();
        r.setId(user.getId());
        r.setUsername(user.getUsername());
        r.setEmail(user.getEmail());
        r.setPhoneNumber(user.getPhoneNumber());
        r.setRole(user.getRole());
        r.setIsActive(user.getIsActive());
        r.setCreatedAt(user.getCreatedAt());
        return r;
    }
}