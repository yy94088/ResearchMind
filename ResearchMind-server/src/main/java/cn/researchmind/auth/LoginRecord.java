package cn.researchmind.auth;

import java.time.LocalDateTime;

public record LoginRecord(
        long id,
        String ipAddress,
        String userAgent,
        LocalDateTime loginTime
) {
}
