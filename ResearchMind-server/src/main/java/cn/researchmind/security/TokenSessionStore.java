package cn.researchmind.security;

import java.time.Duration;
import java.time.Instant;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class TokenSessionStore {

    private static final String KEY_PREFIX = "auth:token:";

    private final StringRedisTemplate redisTemplate;

    public TokenSessionStore(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void save(IssuedToken token, String userId) {
        Duration ttl = Duration.between(Instant.now(), token.expiresAt());
        redisTemplate.opsForValue().set(KEY_PREFIX + token.tokenId(), userId, ttl);
    }

    public boolean isActive(String tokenId, String userId) {
        String storedUserId = redisTemplate.opsForValue().get(KEY_PREFIX + tokenId);
        return userId.equals(storedUserId);
    }

    public void remove(String tokenId) {
        redisTemplate.delete(KEY_PREFIX + tokenId);
    }
}
