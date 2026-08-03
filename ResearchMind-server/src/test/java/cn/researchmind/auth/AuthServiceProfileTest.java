package cn.researchmind.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Optional;

import cn.researchmind.common.ApiException;
import cn.researchmind.security.IssuedToken;
import cn.researchmind.security.JwtService;
import cn.researchmind.security.TokenSessionStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthServiceProfileTest {

    @Mock private UserAccountRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtService jwtService;
    @Mock private TokenSessionStore tokenSessionStore;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(
                userRepository,
                passwordEncoder,
                jwtService,
                tokenSessionStore
        );
    }

    @Test
    void shouldNormalizeAndPersistCurrentUserProfile() {
        UserAccount updated = user("new@example.com", "新姓名", "新机构");
        when(userRepository.emailExistsExcluding("new@example.com", "user-1")).thenReturn(false);
        when(userRepository.updateProfile(
                "user-1",
                "new@example.com",
                "新姓名",
                "新机构",
                "图学习",
                null
        )).thenReturn(1);
        when(userRepository.findById("user-1")).thenReturn(Optional.of(updated));

        UserProfile result = authService.updateProfile("user-1", new ProfileUpdateRequest(
                " 新姓名 ",
                " NEW@EXAMPLE.COM ",
                " 新机构 ",
                " 图学习 ",
                " "
        ));

        assertThat(result.email()).isEqualTo("new@example.com");
        assertThat(result.realName()).isEqualTo("新姓名");
        assertThat(result.institution()).isEqualTo("新机构");
    }

    @Test
    void shouldMakeFirstRegisteredUserAnAdministrator() {
        UserAccount administrator = new UserAccount(
                "admin-1",
                "first_user",
                "encoded-password",
                "first@example.com",
                "首位用户",
                null,
                null,
                null,
                null,
                "ADMIN",
                "ACTIVE",
                LocalDateTime.of(2026, 7, 31, 10, 0)
        );
        when(userRepository.countUsers()).thenReturn(0);
        when(passwordEncoder.encode("password-123")).thenReturn("encoded-password");
        when(userRepository.findById(anyString())).thenReturn(Optional.of(administrator));
        when(jwtService.issue(administrator, false)).thenReturn(new IssuedToken(
                "token",
                "token-id",
                Instant.parse("2026-07-31T04:00:00Z")
        ));

        AuthResponse response = authService.register(new RegisterRequest(
                "first_user",
                "first@example.com",
                "首位用户",
                "password-123"
        ));

        verify(userRepository).insert(
                anyString(),
                eq("first_user"),
                eq("encoded-password"),
                eq("first@example.com"),
                eq("首位用户"),
                eq("ADMIN")
        );
        assertThat(response.user().role()).isEqualTo("ADMIN");
    }

    @Test
    void shouldRejectIncorrectCurrentPassword() {
        UserAccount user = user("old@example.com", "用户", null);
        when(userRepository.findById("user-1")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", user.passwordHash())).thenReturn(false);

        assertThatThrownBy(() -> authService.changePassword(
                "user-1",
                new PasswordChangeRequest("wrong", "new-password")
        ))
                .isInstanceOf(ApiException.class)
                .hasMessage("当前密码不正确");
    }

    @Test
    void shouldEncodeAndPersistNewPassword() {
        UserAccount user = user("old@example.com", "用户", null);
        when(userRepository.findById("user-1")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("current", user.passwordHash())).thenReturn(true);
        when(passwordEncoder.matches("new-password", user.passwordHash())).thenReturn(false);
        when(passwordEncoder.encode("new-password")).thenReturn("new-hash");
        when(userRepository.updatePasswordIfCurrent(
                "user-1",
                "old-hash",
                "new-hash"
        )).thenReturn(1);

        authService.changePassword(
                "user-1",
                new PasswordChangeRequest("current", "new-password")
        );

        verify(userRepository).updatePasswordIfCurrent(
                "user-1",
                "old-hash",
                "new-hash"
        );
    }

    @Test
    void shouldVerifyCurrentPasswordWithoutChangingIt() {
        UserAccount user = user("old@example.com", "用户", null);
        when(userRepository.findById("user-1")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("current", user.passwordHash())).thenReturn(true);

        authService.verifyCurrentPassword("user-1", "current");

        verify(passwordEncoder).matches("current", "old-hash");
    }

    @Test
    void shouldRejectConcurrentPasswordChange() {
        UserAccount user = user("old@example.com", "用户", null);
        when(userRepository.findById("user-1")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("current", user.passwordHash())).thenReturn(true);
        when(passwordEncoder.matches("new-password", user.passwordHash())).thenReturn(false);
        when(passwordEncoder.encode("new-password")).thenReturn("new-hash");
        when(userRepository.updatePasswordIfCurrent(
                "user-1",
                "old-hash",
                "new-hash"
        )).thenReturn(0);

        assertThatThrownBy(() -> authService.changePassword(
                "user-1",
                new PasswordChangeRequest("current", "new-password")
        ))
                .isInstanceOf(ApiException.class)
                .hasMessage("密码已在其他请求中更新，请使用最新密码重试");
    }

    private UserAccount user(String email, String realName, String institution) {
        return new UserAccount(
                "user-1",
                "researcher",
                "old-hash",
                email,
                realName,
                null,
                institution,
                "图学习",
                null,
                "USER",
                "ACTIVE",
                LocalDateTime.of(2026, 7, 30, 10, 0)
        );
    }
}
