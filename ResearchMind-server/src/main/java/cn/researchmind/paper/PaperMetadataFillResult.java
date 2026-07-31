package cn.researchmind.paper;

import java.util.List;

public record PaperMetadataFillResult(
        PaperView paper,
        List<String> filledFields
) {
}
