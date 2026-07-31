package cn.researchmind.ai;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
record GeneratedAnalysis(
        String summary,
        String background,
        String methodOverview,
        List<String> contributions,
        List<String> innovations,
        List<String> limitations,
        List<String> futureDirections,
        List<String> methodSteps,
        String experimentConclusion,
        Integer innovationScore,
        GeneratedPaperMetadata metadata
) {
}
