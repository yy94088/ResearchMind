package cn.researchmind.activity;

import java.time.LocalDateTime;

public record RecentActivityView(
        long id,
        String type,
        String title,
        String detail,
        LocalDateTime occurredAt
) {
}
