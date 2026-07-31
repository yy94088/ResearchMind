package cn.researchmind.team;

import java.time.LocalDateTime;

public record TeamInvitationView(
        String teamId,
        String teamName,
        String institution,
        String inviterName,
        String role,
        LocalDateTime invitedAt
) {
}
