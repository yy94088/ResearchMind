package cn.researchmind.preference;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class UserPreferenceService {

    private static final String KEY_PREFIX = "user:preferences:";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public UserPreferenceService(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper
    ) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    public UserPreferences find(String userId) {
        String value = redisTemplate.opsForValue().get(KEY_PREFIX + userId);
        if (value == null || value.isBlank()) return UserPreferences.defaults();
        try {
            JsonNode node = objectMapper.readTree(value);
            UserPreferences defaults = UserPreferences.defaults();
            return new UserPreferences(
                    booleanValue(node, "resumeReading", defaults.resumeReading()),
                    booleanValue(
                            node,
                            "autoSaveReadingProgress",
                            defaults.autoSaveReadingProgress()
                    ),
                    booleanValue(
                            node,
                            "confirmPaperDeletion",
                            defaults.confirmPaperDeletion()
                    ),
                    booleanValue(node, "defaultGridView", defaults.defaultGridView())
            );
        } catch (JsonProcessingException exception) {
            return UserPreferences.defaults();
        }
    }

    public UserPreferences save(String userId, UserPreferences preferences) {
        try {
            redisTemplate.opsForValue().set(
                    KEY_PREFIX + userId,
                    objectMapper.writeValueAsString(preferences)
            );
            return preferences;
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("用户偏好序列化失败", exception);
        }
    }

    private boolean booleanValue(
            JsonNode node,
            String field,
            boolean defaultValue
    ) {
        JsonNode value = node.get(field);
        return value != null && value.isBoolean()
                ? value.booleanValue()
                : defaultValue;
    }
}
