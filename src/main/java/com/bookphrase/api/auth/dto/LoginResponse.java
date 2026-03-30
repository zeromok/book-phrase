package com.bookphrase.api.auth.dto;

public record LoginResponse(
        String accessToken,
        String tokenType,
        String nickname
) {
    public static LoginResponse of(String accessToken, String nickname) {
        return new LoginResponse(accessToken, "Bearer", nickname);
    }
}
