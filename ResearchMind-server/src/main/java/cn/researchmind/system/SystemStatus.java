package cn.researchmind.system;

import java.time.OffsetDateTime;

public record SystemStatus(
        String application,
        String database,
        int databaseTableCount,
        String redis,
        String objectStorage,
        OffsetDateTime checkedAt
) {
}
