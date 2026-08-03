package cn.researchmind.upload;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import cn.researchmind.paper.ResearchAreaCandidate;

@JsonIgnoreProperties(ignoreUnknown = true)
record GeneratedPaperMetadata(
        String title,
        String titleZh,
        List<String> authors,
        List<String> institutions,
        List<String> keywords,
        @JsonProperty("abstract") String abstractText,
        String doi,
        Integer year,
        String journal,
        List<ResearchAreaCandidate> areas
) {
}
