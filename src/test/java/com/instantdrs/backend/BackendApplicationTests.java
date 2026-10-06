package com.instantdrs.backend;

import com.instantdrs.backend.entity.User;
import com.instantdrs.backend.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class BackendApplicationTests {

    @Autowired
    private UserRepository userRepository;

    @Test
    void contextLoads() {
    }

    @Test
    void verifyAdminPasswordIsHashedWithBCrypt() {
        Optional<User> adminOpt = userRepository.findByUsername("admin");
        assertTrue(adminOpt.isPresent(), "User admin should exist in the database");
        User admin = adminOpt.get();

        assertNotEquals("admin123", admin.getPassword(), "Password MUST NOT be stored as plain text");
        assertTrue(admin.getPassword().startsWith("$2a$") || admin.getPassword().startsWith("$2b$"),
                "Password must be a BCrypt hash");

        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        assertTrue(encoder.matches("admin123", admin.getPassword()),
                "BCrypt hash must match original password");
    }
}
