package cn.researchmind.paper;

import java.util.List;

public record PaperMetadataCompletion(
        String titleZh,
        List<String> authors,
        List<String> keywords,
        String abstractText,
        String doi,
        Integer year,
        String journal,
        List<PaperAreaView> areas
) {
}
