package cn.researchmind.ai;

import java.time.OffsetDateTime;

public record AiQuestionView(
        String analysisId,
        String paperId,
        String question,
        String answer,
        String model,
        int tokenUsage,
        OffsetDateTime answeredAt
) {
}
