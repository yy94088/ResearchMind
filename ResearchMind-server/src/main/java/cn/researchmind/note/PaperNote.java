package cn.researchmind.note;

import java.time.LocalDateTime;

public record PaperNote(
        String id,
        String paperId,
        String content,
        String visibility,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
