package cn.researchmind.auth;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.researchmind.common.ApiException;
import cn.researchmind.config.PasswordResetProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class PasswordResetServiceTest {

    @Mock private UserAccountRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ValueOperations<String, String> valueOperations;
    @Mock private JavaMailSender mailSender;

    private PasswordResetProperties properties;
    private PasswordResetService service;

    @BeforeEach
    void setUp() {
        properties = new PasswordResetProperties();
        service = new PasswordResetService(
                userRepository,
                passwordEncoder,
                redisTemplate,
                mailSender,
                properties
        );
    }

    @Test
    void shouldClearlyRejectRequestsWhenMailIsNotConfigured() {
        assertThatThrownBy(() ->
                service.request(new PasswordResetRequest("user@example.com")))
                .isInstanceOf(ApiException.class)
                .hasMessage("密码找回邮件服务尚未配置，请联系管理员");
    }

    @Test
    void shouldConsumeOneTimeTokenAndUpdatePassword() {
        properties.setEnabled(true);
        properties.setFromEmail("noreply@example.com");
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.getAndDelete(startsWith("auth:password-reset:")))
                .thenReturn("user-1");
        when(passwordEncoder.encode("new-password")).thenReturn("new-hash");
        when(userRepository.updatePassword("user-1", "new-hash")).thenReturn(1);

        service.confirm(new PasswordResetConfirmRequest("valid-token", "new-password"));

        verify(userRepository).updatePassword("user-1", "new-hash");
    }
}
