package cn.researchmind.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;

import cn.researchmind.common.ApiException;
import cn.researchmind.config.PasswordResetProperties;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class PasswordResetService {

    private static final String KEY_PREFIX = "auth:password-reset:";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final UserAccountRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final StringRedisTemplate redisTemplate;
    private final JavaMailSender mailSender;
    private final PasswordResetProperties properties;

    public PasswordResetService(
            UserAccountRepository userRepository,
            PasswordEncoder passwordEncoder,
            StringRedisTemplate redisTemplate,
            JavaMailSender mailSender,
            PasswordResetProperties properties
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.redisTemplate = redisTemplate;
        this.mailSender = mailSender;
        this.properties = properties;
    }

    public PasswordResetResponse request(PasswordResetRequest request) {
        ensureEnabled();
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        UserAccount user = userRepository.findByEmail(email).orElse(null);
        String genericMessage = "如果该邮箱已注册，重置邮件将在几分钟内送达";
        if (user == null) return new PasswordResetResponse(genericMessage);

        byte[] randomBytes = new byte[32];
        SECURE_RANDOM.nextBytes(randomBytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
        String redisKey = KEY_PREFIX + hash(token);
        redisTemplate.opsForValue().set(
                redisKey,
                user.id(),
                properties.getExpiration()
        );

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(properties.getFromEmail());
        message.setTo(user.email());
        message.setSubject("ResearchMind 密码重置");
        message.setText("""
                你好，%s：

                请在 %d 分钟内打开以下链接重置 ResearchMind 登录密码：
                %s?resetToken=%s

                如果这不是你的操作，请忽略此邮件。
                """.formatted(
                user.realName(),
                properties.getExpiration().toMinutes(),
                properties.getFrontendUrl(),
                token
        ));
        try {
            mailSender.send(message);
        } catch (MailException exception) {
            redisTemplate.delete(redisKey);
            throw new ApiException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "PASSWORD_RESET_EMAIL_FAILED",
                    "密码重置邮件发送失败，请检查 SMTP 配置"
            );
        }
        return new PasswordResetResponse(genericMessage);
    }

    public void confirm(PasswordResetConfirmRequest request) {
        ensureEnabled();
        String userId = redisTemplate.opsForValue().getAndDelete(
                KEY_PREFIX + hash(request.token().trim())
        );
        if (userId == null) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "PASSWORD_RESET_TOKEN_INVALID",
                    "密码重置链接无效或已过期"
            );
        }
        if (userRepository.updatePassword(
                userId,
                passwordEncoder.encode(request.newPassword())
        ) == 0) {
            throw new ApiException(
                    HttpStatus.NOT_FOUND,
                    "USER_NOT_FOUND",
                    "用户不存在"
            );
        }
    }

    private void ensureEnabled() {
        if (!properties.isEnabled()
                || properties.getFromEmail() == null
                || properties.getFromEmail().isBlank()) {
            throw new ApiException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "PASSWORD_RESET_NOT_CONFIGURED",
                    "密码找回邮件服务尚未配置，请联系管理员"
            );
        }
    }

    private String hash(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256")
                            .digest(value.getBytes(StandardCharsets.UTF_8))
            );
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前 Java 环境不支持 SHA-256", exception);
        }
    }
}
