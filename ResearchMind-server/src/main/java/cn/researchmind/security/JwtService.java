package cn.researchmind.security;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import javax.crypto.SecretKey;

import cn.researchmind.auth.UserAccount;
import cn.researchmind.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

    private final JwtProperties properties;
    private final SecretKey signingKey;

    public JwtService(JwtProperties properties) {
        this.properties = properties;
        try {
            byte[] keyBytes = Decoders.BASE64.decode(properties.secret());
            this.signingKey = Keys.hmacShaKeyFor(keyBytes);
        } catch (RuntimeException exception) {
            throw new IllegalStateException(
                    "JWT_SECRET 必须是有效的 Base64 字符串，解码后不少于 32 字节",
                    exception
            );
        }
    }

    public IssuedToken issue(UserAccount user, boolean rememberMe) {
        Instant issuedAt = Instant.now();
        Duration lifetime = rememberMe
                ? properties.rememberExpiration()
                : properties.expiration();
        Instant expiresAt = issuedAt.plus(lifetime);
        String tokenId = UUID.randomUUID().toString();

        String value = Jwts.builder()
                .id(tokenId)
                .subject(user.id())
                .claim("username", user.username())
                .claim("role", user.role())
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiresAt))
                .signWith(signingKey)
                .compact();

        return new IssuedToken(value, tokenId, expiresAt);
    }

    public Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
