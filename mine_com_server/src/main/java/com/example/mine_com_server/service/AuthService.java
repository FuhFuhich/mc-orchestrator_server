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
import com.example.mine_com_server.dto.request.UpdateProfileRequest;
import com.example.mine_com_server.dto.request.ChangePasswordRequest;

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

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (request.getEmail() != null && !request.getEmail().isBlank()
                && userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalStateException("Email уже занят: " + request.getEmail());
        }
        if (request.getPhoneNumber() != null && !request.getPhoneNumber().isBlank()
                && userRepository.existsByPhoneNumber(request.getPhoneNumber())) {
            throw new IllegalStateException("Телефон уже занят: " + request.getPhoneNumber());
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
        log.info("[AUTH] Зарегистрирован: {}", user.getUsername());
        return buildAuthResponse(user);
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        String identity = request.getIdentity();

        // Ищем пользователя по email, username или телефону
        User user = userRepository.findByEmail(identity)
                .or(() -> userRepository.findByUsername(identity))
                .or(() -> userRepository.findByPhoneNumber(identity))
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

    @Transactional
    public AuthResponse refresh(String refreshTokenValue) {
        UUID userId = refreshTokenService.validate(refreshTokenValue);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("Пользователь не найден"));
        return buildAuthResponse(user);
    }

    @Transactional
    public void logout(UUID userId) {
        refreshTokenService.revokeByUserId(userId);
        log.info("[AUTH] Выход: {}", userId);
    }

    public UserResponse getMe(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("Пользователь не найден"));
        return toUserResponse(user);
    }

    private AuthResponse buildAuthResponse(User user) {
        String accessToken  = jwtService.generateToken(user.getId());
        String refreshToken = refreshTokenService.create(user.getId());
        return new AuthResponse(accessToken, refreshToken, user.getUsername(), user.getEmail(), user.getRole());
    }

    @Transactional
    public UserResponse updateMe(UUID userId, UpdateProfileRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("Пользователь не найден"));

        if (request.getUsername() != null && !request.getUsername().equals(user.getUsername())) {
            if (userRepository.existsByUsername(request.getUsername()))
                throw new IllegalStateException("Имя пользователя занято");
            user.setUsername(request.getUsername());
        }
        if (request.getEmail() != null && !request.getEmail().equals(user.getEmail())) {
            if (userRepository.existsByEmail(request.getEmail()))
                throw new IllegalStateException("Email уже занят");
            user.setEmail(request.getEmail());
        }
        if (request.getPhoneNumber() != null) {
            user.setPhoneNumber(request.getPhoneNumber());
        }

        user = userRepository.save(user);
        log.info("[USER] Профиль обновлён: {}", user.getUsername());
        return toUserResponse(user);
    }

    @Transactional
    public void changePassword(UUID userId, ChangePasswordRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("Пользователь не найден"));

        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        userId.toString(),
                        request.getCurrentPassword()
                )
        );

        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
        log.info("[USER] Пароль изменён: {}", user.getUsername());
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
        r.setAvatarUrl(user.getAvatarUrl());
        return r;
    }
}