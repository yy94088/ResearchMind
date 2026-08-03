package cn.researchmind.admin;

import java.time.LocalDateTime;

public record AdminAuditView(
        String id,
        String type,
        String actor,
        String module,
        String action,
        boolean success,
        String ipAddress,
        LocalDateTime occurredAt
) {
}
