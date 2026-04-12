package com.example.mine_com_server.exception;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;

import static org.junit.jupiter.api.Assertions.*;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
    }

    @Test
    void handleNotFound_returns404() {
        ResponseEntity<ErrorResponse> response = handler.handleNotFound(new NotFoundException("Объект не найден"));

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(404, response.getBody().getStatus());
        assertEquals("Объект не найден", response.getBody().getMessage());
    }

    @Test
    void handleForbidden_returns403() {
        ResponseEntity<ErrorResponse> response = handler.handleForbidden(new ForbiddenException("Нет прав"));

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertEquals("Нет прав", response.getBody().getMessage());
    }

    @Test
    void handleBadCredentials_returns401_withFixedMessage() {
        ResponseEntity<ErrorResponse> response = handler.handleBadCredentials(new BadCredentialsException("bad creds"));

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertEquals("Неверный логин или пароль", response.getBody().getMessage());
    }

    @Test
    void handleAccessDenied_returns403_withFixedMessage() {
        ResponseEntity<ErrorResponse> response = handler.handleAccessDenied(new AccessDeniedException("denied"));

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertEquals("Доступ запрещён", response.getBody().getMessage());
    }

    @Test
    void handleSsh_returns502_withPrefixedMessage() {
        ResponseEntity<ErrorResponse> response = handler.handleSsh(new SshException("Session.connect timeout"));

        assertEquals(HttpStatus.BAD_GATEWAY, response.getStatusCode());
        assertTrue(response.getBody().getMessage().contains("Ошибка SSH: Session.connect timeout"));
    }

    @Test
    void handleMaxUploadSize_returns413() {
        ResponseEntity<ErrorResponse> response = handler.handleMaxUploadSize(new MaxUploadSizeExceededException(2300L));

        assertEquals(HttpStatus.PAYLOAD_TOO_LARGE, response.getStatusCode());
        assertTrue(response.getBody().getMessage().contains("Размер архива превышает допустимый лимит"));
    }

    @Test
    void handleMultipart_returns400_withOriginalCauseMessage() {
        MultipartException ex = new MultipartException("multipart error", new IllegalStateException("stream closed"));

        ResponseEntity<ErrorResponse> response = handler.handleMultipart(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertTrue(response.getBody().getMessage().contains("stream closed"));
    }

    @Test
    void handleTooManyRequests_returns429() {
        ResponseEntity<ErrorResponse> response = handler.handleTooManyRequests(
                new TooManyRequestsException("Слишком много запросов")
        );

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, response.getStatusCode());
        assertEquals("Слишком много запросов", response.getBody().getMessage());
    }

    @Test
    void handleAll_returns500() {
        ResponseEntity<ErrorResponse> response = handler.handleAll(new RuntimeException("boom"));

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals("Внутренняя ошибка сервера", response.getBody().getMessage());
    }
}
