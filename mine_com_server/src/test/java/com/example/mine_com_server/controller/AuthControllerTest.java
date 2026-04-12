package com.example.mine_com_server.controller;

import com.example.mine_com_server.dto.request.LoginRequest;
import com.example.mine_com_server.dto.request.RefreshRequest;
import com.example.mine_com_server.dto.request.RegisterRequest;
import com.example.mine_com_server.dto.response.AuthResponse;
import com.example.mine_com_server.dto.response.UserResponse;
import com.example.mine_com_server.service.AuthService;
import com.example.mine_com_server.service.RateLimitService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private AuthService authService;

    @Mock
    private RateLimitService rateLimitService;

    private AuthController authController;

    @BeforeEach
    void setUp() {
        authController = new AuthController(authService, rateLimitService);
    }

    @Test
    void register_usesFirstForwardedIp_andReturnsCreated() {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("ilya");
        request.setPassword("password123");
        request.setEmail("ilya@example.com");

        AuthResponse responseBody = new AuthResponse("access", "refresh", "ilya", "ilya@example.com", "user");
        when(authService.register(request)).thenReturn(responseBody);

        MockHttpServletRequest httpRequest = new MockHttpServletRequest();
        httpRequest.addHeader("X-Forwarded-For", "203.0.113.10, 10.0.0.1");
        httpRequest.setRemoteAddr("127.0.0.1");

        ResponseEntity<AuthResponse> response = authController.register(request, httpRequest);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertSame(responseBody, response.getBody());
        verify(rateLimitService).checkRegister("203.0.113.10");
        verify(authService).register(request);
    }

    @Test
    void login_usesRemoteAddr_whenNoForwardedHeader() {
        LoginRequest request = new LoginRequest();
        request.setIdentity("ilya");
        request.setPassword("password123");

        AuthResponse responseBody = new AuthResponse("access", "refresh", "ilya", "ilya@example.com", "user");
        when(authService.login(request)).thenReturn(responseBody);

        MockHttpServletRequest httpRequest = new MockHttpServletRequest();
        httpRequest.setRemoteAddr("192.168.1.77");

        ResponseEntity<AuthResponse> response = authController.login(request, httpRequest);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertSame(responseBody, response.getBody());
        verify(rateLimitService).checkLogin("192.168.1.77");
        verify(authService).login(request);
    }

    @Test
    void refresh_returnsOk_withAuthResponse() {
        RefreshRequest request = new RefreshRequest();
        request.setRefreshToken("refresh-token-value");

        AuthResponse responseBody = new AuthResponse("new-access", "new-refresh", "ilya", "ilya@example.com", "user");
        when(authService.refresh("refresh-token-value")).thenReturn(responseBody);

        ResponseEntity<AuthResponse> response = authController.refresh(request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertSame(responseBody, response.getBody());
        verify(authService).refresh("refresh-token-value");
    }

    @Test
    void logout_callsService_andReturnsNoContent() {
        UUID userId = UUID.randomUUID();
        UserDetails userDetails = User.withUsername(userId.toString())
                .password("ignored")
                .authorities("ROLE_USER")
                .build();

        ResponseEntity<Void> response = authController.logout(userDetails);

        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        assertNull(response.getBody());
        verify(authService).logout(userId);
    }

    @Test
    void me_returnsCurrentUserProfile() {
        UUID userId = UUID.randomUUID();
        UserDetails userDetails = User.withUsername(userId.toString())
                .password("ignored")
                .authorities("ROLE_USER")
                .build();

        UserResponse userResponse = new UserResponse();
        userResponse.setId(userId);
        userResponse.setUsername("ilya");
        when(authService.getMe(userId)).thenReturn(userResponse);

        ResponseEntity<UserResponse> response = authController.me(userDetails);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertSame(userResponse, response.getBody());
        verify(authService).getMe(userId);
    }
}
