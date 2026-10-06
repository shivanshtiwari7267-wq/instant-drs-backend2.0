package com.instantdrs.backend.service;

import com.instantdrs.backend.dto.AuthResponse;
import com.instantdrs.backend.dto.LoginRequest;
import com.instantdrs.backend.dto.RegisterRequest;
import com.instantdrs.backend.entity.User;
import com.instantdrs.backend.repository.UserRepository;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final BCryptPasswordEncoder passwordEncoder;

    public AuthService(UserRepository userRepository) {
        this.userRepository = userRepository;
        this.passwordEncoder = new BCryptPasswordEncoder();
    }

    public AuthResponse register(RegisterRequest request) {
        if (request == null) {
            return AuthResponse.failure("Username and password are required");
        }

        String username = request.getUsername();
        String password = request.getPassword();

        if (username == null || username.trim().isEmpty()) {
            return AuthResponse.failure("Username cannot be blank");
        }

        if (password == null || password.trim().isEmpty()) {
            return AuthResponse.failure("Password cannot be blank");
        }

        String trimmedUsername = username.trim();

        if (trimmedUsername.length() > 50) {
            return AuthResponse.failure("Username cannot exceed 50 characters");
        }

        if (password.length() < 4) {
            return AuthResponse.failure("Password must be at least 4 characters");
        }

        if (userRepository.existsByUsername(trimmedUsername)) {
            return AuthResponse.failure("Username already exists");
        }

        String hashedPassword = passwordEncoder.encode(password);
        User user = new User(trimmedUsername, hashedPassword);
        User savedUser = userRepository.save(user);

        return AuthResponse.success("Registration successful", savedUser.getId(), savedUser.getUsername());
    }

    public AuthResponse login(LoginRequest request) {
        if (request == null) {
            return AuthResponse.failure("Invalid username or password");
        }

        String username = request.getUsername();
        String password = request.getPassword();

        if (username == null || username.trim().isEmpty() || password == null || password.isEmpty()) {
            return AuthResponse.failure("Invalid username or password");
        }

        String trimmedUsername = username.trim();

        Optional<User> userOptional = userRepository.findByUsername(trimmedUsername);
        if (userOptional.isEmpty()) {
            return AuthResponse.failure("Invalid username or password");
        }

        User user = userOptional.get();
        if (!passwordEncoder.matches(password, user.getPassword())) {
            return AuthResponse.failure("Invalid username or password");
        }

        return AuthResponse.success("Login successful", user.getId(), user.getUsername());
    }
}
