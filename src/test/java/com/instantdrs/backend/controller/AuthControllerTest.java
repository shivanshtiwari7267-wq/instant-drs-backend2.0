package com.instantdrs.backend.controller;

import com.instantdrs.backend.dto.AuthResponse;
import com.instantdrs.backend.dto.LoginRequest;
import com.instantdrs.backend.dto.RegisterRequest;
import com.instantdrs.backend.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private AuthService authService;

    private AuthController authController;

    @BeforeEach
    void setUp() {
        authController = new AuthController(authService);
    }

    @Test
    void register_Success() {
        RegisterRequest request = new RegisterRequest("admin", "admin123");
        AuthResponse expected = AuthResponse.success("Registration successful", 1L, "admin");
        when(authService.register(any(RegisterRequest.class))).thenReturn(expected);

        ResponseEntity<AuthResponse> responseEntity = authController.register(request);

        assertNotNull(responseEntity);
        assertEquals(200, responseEntity.getStatusCode().value());
        AuthResponse body = responseEntity.getBody();
        assertNotNull(body);
        assertTrue(body.isSuccess());
        assertEquals("Registration successful", body.getMessage());
        assertEquals(1L, body.getUserId());
        assertEquals("admin", body.getUsername());

        verify(authService).register(request);
    }

    @Test
    void register_Failure() {
        RegisterRequest request = new RegisterRequest("admin", "admin123");
        AuthResponse expected = AuthResponse.failure("Username already exists");
        when(authService.register(any(RegisterRequest.class))).thenReturn(expected);

        ResponseEntity<AuthResponse> responseEntity = authController.register(request);

        assertNotNull(responseEntity);
        assertEquals(200, responseEntity.getStatusCode().value());
        AuthResponse body = responseEntity.getBody();
        assertNotNull(body);
        assertFalse(body.isSuccess());
        assertEquals("Username already exists", body.getMessage());
        assertNull(body.getUserId());
        assertNull(body.getUsername());

        verify(authService).register(request);
    }

    @Test
    void login_Success() {
        LoginRequest request = new LoginRequest("admin", "admin123");
        AuthResponse expected = AuthResponse.success("Login successful", 1L, "admin");
        when(authService.login(any(LoginRequest.class))).thenReturn(expected);

        ResponseEntity<AuthResponse> responseEntity = authController.login(request);

        assertNotNull(responseEntity);
        assertEquals(200, responseEntity.getStatusCode().value());
        AuthResponse body = responseEntity.getBody();
        assertNotNull(body);
        assertTrue(body.isSuccess());
        assertEquals("Login successful", body.getMessage());
        assertEquals(1L, body.getUserId());
        assertEquals("admin", body.getUsername());

        verify(authService).login(request);
    }

    @Test
    void login_Failure() {
        LoginRequest request = new LoginRequest("admin", "wrongpassword");
        AuthResponse expected = AuthResponse.failure("Invalid username or password");
        when(authService.login(any(LoginRequest.class))).thenReturn(expected);

        ResponseEntity<AuthResponse> responseEntity = authController.login(request);

        assertNotNull(responseEntity);
        assertEquals(200, responseEntity.getStatusCode().value());
        AuthResponse body = responseEntity.getBody();
        assertNotNull(body);
        assertFalse(body.isSuccess());
        assertEquals("Invalid username or password", body.getMessage());
        assertNull(body.getUserId());
        assertNull(body.getUsername());

        verify(authService).login(request);
    }
}
