package com.example.mine_com_server.controller;

import com.example.mine_com_server.dto.request.ChangePasswordRequest;
import com.example.mine_com_server.dto.request.UpdateProfileRequest;
import com.example.mine_com_server.dto.response.UserResponse;
import com.example.mine_com_server.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

    private final AuthService authService;

    @GetMapping("/me")
    public ResponseEntity<UserResponse> getMe(Authentication auth) {
        UUID userId = UUID.fromString(auth.getName());
        return ResponseEntity.ok(authService.getMe(userId));
    }

    @PutMapping("/me")
    public ResponseEntity<UserResponse> updateMe(
            Authentication auth,
            @Valid @RequestBody UpdateProfileRequest request) {
        UUID userId = UUID.fromString(auth.getName());
        return ResponseEntity.ok(authService.updateMe(userId, request));
    }

    @PutMapping("/me/password")
    public ResponseEntity<Void> changePassword(
            Authentication auth,
            @Valid @RequestBody ChangePasswordRequest request) {
        UUID userId = UUID.fromString(auth.getName());
        authService.changePassword(userId, request);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/find")
    public ResponseEntity<UserResponse> findByUsername(@RequestParam String username) {
        return ResponseEntity.ok(authService.findByUsername(username));
    }
}