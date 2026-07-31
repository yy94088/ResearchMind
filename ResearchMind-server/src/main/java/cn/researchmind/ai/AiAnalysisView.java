package cn.researchmind.ai;

import java.time.OffsetDateTime;
import java.util.List;

public record AiAnalysisView(
        String analysisId,
        String paperId,
        String summary,
        String background,
        String methodOverview,
        List<String> contributions,
        List<String> innovations,
        List<String> limitations,
        List<String> futureDirections,
        List<String> methodSteps,
        String experimentConclusion,
        int innovationScore,
        String model,
        int tokenUsage,
        List<String> metadataFilledFields,
        OffsetDateTime generatedAt
) {
}
