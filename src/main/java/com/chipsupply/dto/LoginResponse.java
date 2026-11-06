package com.chipsupply.dto;

public record LoginResponse(String token, String tokenType, long expiresIn,
                            Long userId, String username, String realName, String role) {
}
