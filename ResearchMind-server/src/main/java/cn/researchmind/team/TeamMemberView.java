package cn.researchmind.team;

import java.time.LocalDateTime;

public record TeamMemberView(
        String id,
        String name,
        String email,
        String avatarUrl,
        String role,
        String joinStatus,
        LocalDateTime joinTime,
        int paperCount
) {
}
