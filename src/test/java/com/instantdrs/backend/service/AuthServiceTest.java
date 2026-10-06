package com.instantdrs.backend.service;

import com.instantdrs.backend.dto.AuthResponse;
import com.instantdrs.backend.dto.LoginRequest;
import com.instantdrs.backend.dto.RegisterRequest;
import com.instantdrs.backend.entity.User;
import com.instantdrs.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    private AuthService authService;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepository);
    }

    @Test
    void register_Success() {
        RegisterRequest request = new RegisterRequest("admin", "admin123");
        when(userRepository.existsByUsername("admin")).thenReturn(false);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        when(userRepository.save(userCaptor.capture())).thenAnswer(invocation -> {
            User u = invocation.getArgument(0);
            u.setId(1L);
            return u;
        });

        AuthResponse response = authService.register(request);

        assertTrue(response.isSuccess());
        assertEquals("Registration successful", response.getMessage());
        assertEquals(1L, response.getUserId());
        assertEquals("admin", response.getUsername());

        // Verify password is NOT stored as plain text, but hashed with BCrypt
        User savedUser = userCaptor.getValue();
        assertEquals("admin", savedUser.getUsername());
        assertNotEquals("admin123", savedUser.getPassword());
        assertTrue(passwordEncoder.matches("admin123", savedUser.getPassword()));
    }

    @Test
    void register_DuplicateUsername() {
        RegisterRequest request = new RegisterRequest("admin", "admin123");
        when(userRepository.existsByUsername("admin")).thenReturn(true);

        AuthResponse response = authService.register(request);

        assertFalse(response.isSuccess());
        assertEquals("Username already exists", response.getMessage());
        assertNull(response.getUserId());
        assertNull(response.getUsername());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void register_BlankUsernameOrPassword() {
        AuthResponse emptyUserResp = authService.register(new RegisterRequest("   ", "admin123"));
        assertFalse(emptyUserResp.isSuccess());
        assertEquals("Username cannot be blank", emptyUserResp.getMessage());

        AuthResponse emptyPassResp = authService.register(new RegisterRequest("admin", "   "));
        assertFalse(emptyPassResp.isSuccess());
        assertEquals("Password cannot be blank", emptyPassResp.getMessage());

        AuthResponse nullUserResp = authService.register(new RegisterRequest(null, "admin123"));
        assertFalse(nullUserResp.isSuccess());

        AuthResponse nullPassResp = authService.register(new RegisterRequest("admin", null));
        assertFalse(nullPassResp.isSuccess());
    }

    @Test
    void register_ValidationLimits() {
        String longUsername = "a".repeat(51);
        AuthResponse longUserResp = authService.register(new RegisterRequest(longUsername, "admin123"));
        assertFalse(longUserResp.isSuccess());
        assertEquals("Username cannot exceed 50 characters", longUserResp.getMessage());

        AuthResponse shortPassResp = authService.register(new RegisterRequest("admin", "123"));
        assertFalse(shortPassResp.isSuccess());
        assertEquals("Password must be at least 4 characters", shortPassResp.getMessage());
    }

    @Test
    void login_Success() {
        String rawPassword = "admin123";
        String encodedPassword = passwordEncoder.encode(rawPassword);
        User user = new User("admin", encodedPassword);
        user.setId(1L);

        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(user));

        LoginRequest request = new LoginRequest("admin", rawPassword);
        AuthResponse response = authService.login(request);

        assertTrue(response.isSuccess());
        assertEquals("Login successful", response.getMessage());
        assertEquals(1L, response.getUserId());
        assertEquals("admin", response.getUsername());
    }

    @Test
    void login_WrongPassword() {
        String rawPassword = "admin123";
        String encodedPassword = passwordEncoder.encode(rawPassword);
        User user = new User("admin", encodedPassword);
        user.setId(1L);

        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(user));

        LoginRequest request = new LoginRequest("admin", "wrongpassword");
        AuthResponse response = authService.login(request);

        assertFalse(response.isSuccess());
        assertEquals("Invalid username or password", response.getMessage());
        assertNull(response.getUserId());
        assertNull(response.getUsername());
    }

    @Test
    void login_UnknownUsername() {
        when(userRepository.findByUsername("unknownUser")).thenReturn(Optional.empty());

        LoginRequest request = new LoginRequest("unknownUser", "admin123");
        AuthResponse response = authService.login(request);

        assertFalse(response.isSuccess());
        assertEquals("Invalid username or password", response.getMessage());
        assertNull(response.getUserId());
        assertNull(response.getUsername());
    }

    @Test
    void login_BlankCredentials() {
        AuthResponse resp1 = authService.login(new LoginRequest("", "admin123"));
        assertFalse(resp1.isSuccess());
        assertEquals("Invalid username or password", resp1.getMessage());

        AuthResponse resp2 = authService.login(new LoginRequest("admin", ""));
        assertFalse(resp2.isSuccess());
        assertEquals("Invalid username or password", resp2.getMessage());

        AuthResponse resp3 = authService.login(null);
        assertFalse(resp3.isSuccess());
    }
}
