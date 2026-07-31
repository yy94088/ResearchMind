package cn.researchmind.team;

import java.time.LocalDateTime;
import java.util.List;

public record TeamWorkspaceView(
        String id,
        String name,
        String description,
        String institution,
        String ownerId,
        String currentUserRole,
        LocalDateTime createdAt,
        int sharedPaperCount,
        int annotationCount,
        List<TeamMemberView> members,
        List<TeamCollectionView> collections,
        List<TeamActivityView> activities
) {
}
