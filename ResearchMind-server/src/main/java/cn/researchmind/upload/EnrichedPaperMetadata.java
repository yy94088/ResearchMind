package cn.researchmind.upload;

import java.util.List;

import cn.researchmind.paper.PaperAreaView;

public record EnrichedPaperMetadata(
        String title,
        String titleZh,
        List<String> authors,
        List<String> keywords,
        String abstractText,
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
