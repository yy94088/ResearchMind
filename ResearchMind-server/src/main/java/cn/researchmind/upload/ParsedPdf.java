package cn.researchmind.upload;

import java.util.List;

public record ParsedPdf(
        String title,
        List<String> authors,
        List<String> keywords,
        String abstractText,
        String doi,
        Integer year,
        int pages
) {
}
