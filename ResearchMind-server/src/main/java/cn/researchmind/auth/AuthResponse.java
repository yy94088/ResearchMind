package cn.researchmind.auth;

import java.time.Instant;

public record AuthResponse(
        String tokenType,
        String accessToken,
        Instant expiresAt,
        UserProfile user
) {
}
