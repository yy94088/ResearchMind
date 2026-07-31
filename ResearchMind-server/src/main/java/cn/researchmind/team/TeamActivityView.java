package cn.researchmind.team;

import java.time.LocalDateTime;

public record TeamActivityView(
        long id,
        String operatorName,
        String operation,
        LocalDateTime occurredAt
) {
}
