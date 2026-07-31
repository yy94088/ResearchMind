package cn.researchmind.team;

import java.time.LocalDateTime;

record TeamInfo(
        String id,
        String name,
        String description,
        String institution,
        String ownerId,
        String currentUserRole,
        LocalDateTime createdAt
) {
}
