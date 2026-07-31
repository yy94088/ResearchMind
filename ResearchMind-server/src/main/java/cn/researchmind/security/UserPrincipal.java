package cn.researchmind.security;

public record UserPrincipal(
        String id,
        String username,
        String role
) {
}
