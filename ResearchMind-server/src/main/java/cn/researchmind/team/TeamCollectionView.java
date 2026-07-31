package cn.researchmind.team;

import java.time.LocalDateTime;
import java.util.List;

public record TeamCollectionView(
        String id,
        String name,
        String description,
        String color,
        int paperCount,
        LocalDateTime createdAt,
        List<String> currentUserPaperIds
) {
}
