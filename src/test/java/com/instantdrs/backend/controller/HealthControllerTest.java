package com.instantdrs.backend.controller;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class HealthControllerTest {

    private final HealthController healthController = new HealthController();

    @Test
    void healthCheckReturnsUp() {
        ResponseEntity<Map<String, Object>> response = healthController.checkHealth();

        assertNotNull(response);
        assertEquals(200, response.getStatusCode().value());
        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        assertEquals("UP", body.get("status"));
        assertEquals("instantdrs-backend", body.get("service"));
        assertNotNull(body.get("timestamp"));
    }
}
