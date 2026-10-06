package com.instantdrs.backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AuthResponse {

    @JsonProperty("success")
    private boolean success;

    private String message;
    private Long userId;
    private String username;

    public static AuthResponse success(String message, Long userId, String username) {
        return new AuthResponse(true, message, userId, username);
    }

    public static AuthResponse failure(String message) {
        return new AuthResponse(false, message, null, null);
    }
}
