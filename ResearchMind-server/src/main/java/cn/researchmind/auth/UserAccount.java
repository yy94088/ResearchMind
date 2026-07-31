package cn.researchmind.auth;

import java.time.LocalDateTime;

public record UserAccount(
        String id,
        String username,
        String passwordHash,
        String email,
        String realName,
        String avatarUrl,
        String institution,
        String researchDirection,
        String bio,
        String role,
        String status,
        LocalDateTime createTime
) {
}
