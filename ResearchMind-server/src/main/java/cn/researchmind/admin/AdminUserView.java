package cn.researchmind.admin;

import java.time.LocalDateTime;

public record AdminUserView(
        String id,
        String username,
        String email,
        String realName,
        String institution,
        String role,
        String status,
        int paperCount,
        int teamCount,
        LocalDateTime lastLoginTime,
        LocalDateTime createTime
) {
}
