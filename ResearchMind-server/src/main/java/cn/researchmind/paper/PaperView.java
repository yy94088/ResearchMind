package cn.researchmind.paper;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

public record PaperView(
        String id,
        String title,
        String titleZh,
        List<String> authors,
        Integer year,
        String journal,
        String doi,
        String area,
        List<PaperAreaView> areas,
        List<String> tags,
        @JsonProperty("abstract") String abstractText,
        boolean favorite,
        boolean read,
        int progress,
        int currentPage,
        int totalReadSeconds,
        Integer pages,
        String fileName,
        boolean fileAvailable,
        LocalDate uploadDate,
        LocalDateTime lastReadTime
) {
}
