package com.example.mine_com_server.service;

import com.example.mine_com_server.dto.request.ChangePasswordRequest;
import com.example.mine_com_server.dto.request.LoginRequest;
import com.example.mine_com_server.dto.request.RegisterRequest;
import com.example.mine_com_server.dto.request.UpdateProfileRequest;
import com.example.mine_com_server.dto.response.AuthResponse;
import com.example.mine_com_server.dto.response.UserResponse;
import com.example.mine_com_server.exception.ForbiddenException;
import com.example.mine_com_server.exception.NotFoundException;
import com.example.mine_com_server.model.RefreshToken;
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

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (request.getEmail() != null
                && !request.getEmail().isBlank()
                && userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalStateException("Email " + request.getEmail());
        }

        if (request.getPhoneNumber() != null
                && !request.getPhoneNumber().isBlank()
                && userRepository.existsByPhoneNumber(request.getPhoneNumber())) {
            throw new IllegalStateException(request.getPhoneNumber());
        }

        if (userRepository.existsByUsername(request.getUsername())) {
            throw new IllegalStateException(request.getUsername());
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
        log.info("AUTH {}", user.getUsername());

        return buildAuthResponse(user);
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        String identity = request.getIdentity();

        User user = userRepository.findByEmail(identity)
                .or(() -> userRepository.findByUsername(identity))
                .or(() -> userRepository.findByPhoneNumber(identity))
                .orElseThrow(() -> new NotFoundException("User not found: " + identity));

        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        user.getId().toString(),
                        request.getPassword()
                )
        );

        log.info("AUTH {}", user.getEmail());
        return buildAuthResponse(user);
    }

    @Transactional
    public AuthResponse refresh(String refreshTokenValue) {
        RefreshToken refreshToken = refreshTokenService.findByRawToken(refreshTokenValue)
                .orElseThrow(() -> new ForbiddenException("Невалидный refresh token"));

        User user = refreshToken.getUser();
        if (user == null) {
            throw new NotFoundException("User not found for refresh token");
        }

        refreshTokenService.deleteByUserId(user.getId());
        return buildAuthResponse(user);
    }

    @Transactional
    public void logout(UUID userId) {
        refreshTokenService.deleteByUserId(userId);
        log.info("AUTH logout {}", userId);
    }

    public UserResponse getMe(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found: " + userId));
        return toUserResponse(user);
    }

    @Transactional
    public UserResponse updateMe(UUID userId, UpdateProfileRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found: " + userId));

        if (request.getUsername() != null && !request.getUsername().equals(user.getUsername())) {
            if (userRepository.existsByUsername(request.getUsername())) {
                throw new IllegalStateException();
            }
            user.setUsername(request.getUsername());
        }

        if (request.getEmail() != null && !request.getEmail().equals(user.getEmail())) {
            if (userRepository.existsByEmail(request.getEmail())) {
                throw new IllegalStateException("Email");
            }
            user.setEmail(request.getEmail());
        }

        if (request.getPhoneNumber() != null) {
            user.setPhoneNumber(request.getPhoneNumber());
        }

        user = userRepository.save(user);
        log.info("USER updated {}", user.getUsername());

        return toUserResponse(user);
    }

    @Transactional
    public void changePassword(UUID userId, ChangePasswordRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found: " + userId));

        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        userId.toString(),
                        request.getCurrentPassword()
                )
        );

        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);

        log.info("USER password changed {}", user.getUsername());
    }

    public UserResponse findByUsername(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new NotFoundException("User not found: " + username));
        return toUserResponse(user);
    }

    private AuthResponse buildAuthResponse(User user) {
        String accessToken = jwtService.generateToken(user.getId());
        String refreshToken = refreshTokenService.create(user);

        return new AuthResponse(
                accessToken,
                refreshToken,
                user.getUsername(),
                user.getEmail(),
                user.getRole()
        );
    }

    private UserResponse toUserResponse(User user) {
        UserResponse response = new UserResponse();
        response.setId(user.getId());
        response.setUsername(user.getUsername());
        response.setEmail(user.getEmail());
        response.setPhoneNumber(user.getPhoneNumber());
        response.setRole(user.getRole());
        response.setIsActive(user.getIsActive());
        response.setCreatedAt(user.getCreatedAt());
        response.setAvatarUrl(user.getAvatarUrl());
        return response;
    }
}