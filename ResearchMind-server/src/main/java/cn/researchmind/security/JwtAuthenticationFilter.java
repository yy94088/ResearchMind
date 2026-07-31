package cn.researchmind.security;

import java.io.IOException;
import java.util.List;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final TokenSessionStore tokenSessionStore;

    public JwtAuthenticationFilter(
            JwtService jwtService,
            TokenSessionStore tokenSessionStore
    ) {
        this.jwtService = jwtService;
        this.tokenSessionStore = tokenSessionStore;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            Claims claims = jwtService.parse(authorization.substring(BEARER_PREFIX.length()));
            String userId = claims.getSubject();
            String tokenId = claims.getId();
            String username = claims.get("username", String.class);
            String role = claims.get("role", String.class);

            if (tokenSessionStore.isActive(tokenId, userId)) {
                UserPrincipal principal = new UserPrincipal(userId, username, role);
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(
                                principal,
                                null,
                                List.of(new SimpleGrantedAuthority("ROLE_" + role))
                        );
                SecurityContextHolder.getContext().setAuthentication(authentication);
                request.setAttribute("jwtTokenId", tokenId);
            }
        } catch (JwtException | IllegalArgumentException ignored) {
            SecurityContextHolder.clearContext();
        }

        filterChain.doFilter(request, response);
    }
}
