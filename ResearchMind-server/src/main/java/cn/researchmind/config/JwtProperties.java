package cn.researchmind.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "researchmind.jwt")
public record JwtProperties(
        String secret,
        Duration expiration,
        Duration rememberExpiration
) {
}
