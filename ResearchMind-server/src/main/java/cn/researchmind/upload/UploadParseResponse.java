package cn.researchmind.upload;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import cn.researchmind.paper.PaperAreaView;

public record UploadParseResponse(
        String uploadId,
        String fileName,
        long fileSize,
        int pages,
        String title,
        String titleZh,
        List<String> authors,
        List<String> tags,
        @JsonProperty("abstract") String abstractText,
        String doi,
        Integer year,
        String journal,
        String area,
        List<PaperAreaView> areas,
        boolean aiEnriched,
        List<String> aiEnrichedFields,
        String aiModel,
        String aiWarning
) {
}
