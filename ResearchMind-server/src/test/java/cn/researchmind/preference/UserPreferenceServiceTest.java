package cn.researchmind.preference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
class UserPreferenceServiceTest {

    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ValueOperations<String, String> valueOperations;

    private UserPreferenceService preferenceService;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        preferenceService = new UserPreferenceService(
                redisTemplate,
                new ObjectMapper()
        );
    }

    @Test
    void shouldMigrateLegacyNotificationPreferencesToFunctionalDefaults() {
        when(valueOperations.get("user:preferences:user-1")).thenReturn("""
                {
                  "weeklyDigest": false,
                  "teamNotifications": false,
                  "aiNotifications": false,
                  "recommendations": true
                }
                """);

        assertThat(preferenceService.find("user-1"))
                .isEqualTo(UserPreferences.defaults());
    }

    @Test
    void shouldReadSavedFunctionalPreferences() {
        when(valueOperations.get("user:preferences:user-1")).thenReturn("""
                {
                  "resumeReading": false,
                  "autoSaveReadingProgress": true,
                  "confirmPaperDeletion": false,
                  "defaultGridView": true
                }
                """);

        assertThat(preferenceService.find("user-1")).isEqualTo(
                new UserPreferences(false, true, false, true)
        );
    }
}
