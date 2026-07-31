package cn.researchmind.auth;

import java.time.LocalDateTime;

public record UserProfile(
        String id,
        String username,
        String email,
        String realName,
        String avatarUrl,
        String institution,
        String researchDirection,
        String bio,
        String role,
        LocalDateTime createTime
) {
    public static UserProfile from(UserAccount user) {
        return new UserProfile(
                user.id(),
                user.username(),
                user.email(),
                user.realName(),
                user.avatarUrl(),
                user.institution(),
                user.researchDirection(),
                user.bio(),
                user.role(),
                user.createTime()
        );
    }
}
