package cn.researchmind.paper;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ResearchAreaCandidate(
        String name,
        Double confidence,
        Boolean primary,
        String evidence
) {
}
