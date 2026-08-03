package cn.researchmind.auth;

import java.util.Locale;
import java.util.List;
import java.util.UUID;

import cn.researchmind.common.ApiException;
import cn.researchmind.security.IssuedToken;
import cn.researchmind.security.JwtService;
import cn.researchmind.security.TokenSessionStore;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserAccountRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final TokenSessionStore tokenSessionStore;

    public AuthService(
            UserAccountRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            TokenSessionStore tokenSessionStore
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.tokenSessionStore = tokenSessionStore;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String username = request.username().trim();
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        String realName = request.realName().trim();

        if (userRepository.usernameExists(username)) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "USERNAME_ALREADY_EXISTS",
                    "该用户名已被使用"
            );
        }
        if (userRepository.emailExists(email)) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "EMAIL_ALREADY_EXISTS",
                    "该邮箱已被注册"
            );
        }

        String userId = UUID.randomUUID().toString();
        userRepository.insert(
                userId,
                username,
                passwordEncoder.encode(request.password()),
                email,
                realName,
                userRepository.countUsers() == 0 ? "ADMIN" : "USER"
        );

        UserAccount user = requireUser(userId);
        return createSession(user, false);
    }

    @Transactional
    public AuthResponse login(
            LoginRequest request,
            String ipAddress,
            String userAgent
    ) {
        String account = request.account().trim();
        UserAccount user = userRepository.findByAccount(account).orElse(null);

        if (user == null || !passwordEncoder.matches(request.password(), user.passwordHash())) {
            userRepository.addLoginLog(
                    user == null ? null : user.id(),
                    account,
                    ipAddress,
                    userAgent,
                    false,
                    "用户名、邮箱或密码错误"
            );
            throw new ApiException(
                    HttpStatus.UNAUTHORIZED,
                    "INVALID_CREDENTIALS",
                    "用户名、邮箱或密码错误"
            );
        }
        if (!"ACTIVE".equals(user.status())) {
            userRepository.addLoginLog(
                    user.id(),
                    account,
                    ipAddress,
                    userAgent,
                    false,
                    "账户不可用"
            );
            throw new ApiException(
                    HttpStatus.FORBIDDEN,
                    "ACCOUNT_NOT_ACTIVE",
                    "账户未激活或已被禁用"
            );
        }

        userRepository.updateLastLogin(user.id());
        userRepository.addLoginLog(
                user.id(),
                account,
                ipAddress,
                userAgent,
                true,
                null
        );
        return createSession(user, request.rememberMe());
    }

    public UserProfile getCurrentUser(String userId) {
        return UserProfile.from(requireUser(userId));
    }

    @Transactional
    public UserProfile updateProfile(String userId, ProfileUpdateRequest request) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        if (userRepository.emailExistsExcluding(email, userId)) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "EMAIL_ALREADY_EXISTS",
                    "该邮箱已被其他账户使用"
            );
        }

        int affected = userRepository.updateProfile(
                userId,
                email,
                request.realName().trim(),
                normalizeOptional(request.institution()),
                normalizeOptional(request.researchDirection()),
                normalizeOptional(request.bio())
        );
        if (affected == 0) throw userNotFound();
        return UserProfile.from(requireUser(userId));
    }

    @Transactional
    public void changePassword(String userId, PasswordChangeRequest request) {
        UserAccount user = requireUser(userId);
        requireMatchingPassword(request.currentPassword(), user);
        if (passwordEncoder.matches(request.newPassword(), user.passwordHash())) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "PASSWORD_UNCHANGED",
                    "新密码不能与当前密码相同"
            );
        }
        String passwordHash = passwordEncoder.encode(request.newPassword());
        if (userRepository.updatePasswordIfCurrent(
                userId,
                user.passwordHash(),
                passwordHash
        ) == 0) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "PASSWORD_CHANGED_CONCURRENTLY",
                    "密码已在其他请求中更新，请使用最新密码重试"
            );
        }
    }

    public void verifyCurrentPassword(String userId, String currentPassword) {
        requireMatchingPassword(currentPassword, requireUser(userId));
    }

    public List<LoginRecord> getRecentLogins(String userId) {
        requireUser(userId);
        return userRepository.findRecentLogins(userId, 5);
    }

    public void logout(String tokenId) {
        if (tokenId != null && !tokenId.isBlank()) {
            tokenSessionStore.remove(tokenId);
        }
    }

    private AuthResponse createSession(UserAccount user, boolean rememberMe) {
        IssuedToken token = jwtService.issue(user, rememberMe);
        tokenSessionStore.save(token, user.id());
        return new AuthResponse(
                "Bearer",
                token.value(),
                token.expiresAt(),
                UserProfile.from(user)
        );
    }

    private UserAccount requireUser(String id) {
        return userRepository.findById(id).orElseThrow(this::userNotFound);
    }

    private String normalizeOptional(String value) {
        if (value == null || value.isBlank()) return null;
        return value.trim();
    }

    private void requireMatchingPassword(
            String currentPassword,
            UserAccount user
    ) {
        if (!passwordEncoder.matches(currentPassword, user.passwordHash())) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "CURRENT_PASSWORD_INCORRECT",
                    "当前密码不正确"
            );
        }
    }

    private ApiException userNotFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "用户不存在");
    }
}
