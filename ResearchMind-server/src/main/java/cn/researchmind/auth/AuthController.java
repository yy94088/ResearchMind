package cn.researchmind.auth;

import cn.researchmind.security.UserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final AvatarService avatarService;
    private final PasswordResetService passwordResetService;

    public AuthController(
            AuthService authService,
            AvatarService avatarService,
            PasswordResetService passwordResetService
    ) {
        this.authService = authService;
        this.avatarService = avatarService;
        this.passwordResetService = passwordResetService;
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(
            @Valid @RequestBody RegisterRequest request
    ) {
        return ResponseEntity.status(201).body(authService.register(request));
    }

    @PostMapping("/login")
    public AuthResponse login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest
    ) {
        return authService.login(
                request,
                clientIp(httpRequest),
                httpRequest.getHeader(HttpHeaders.USER_AGENT)
        );
    }

    @GetMapping("/me")
    public UserProfile me(@AuthenticationPrincipal UserPrincipal principal) {
        return authService.getCurrentUser(principal.id());
    }

    @PutMapping("/me")
    public UserProfile updateMe(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody ProfileUpdateRequest request
    ) {
        return authService.updateProfile(principal.id(), request);
    }

    @PutMapping("/password")
    public ResponseEntity<Void> changePassword(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody PasswordChangeRequest request
    ) {
        authService.changePassword(principal.id(), request);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/password/verify")
    public ResponseEntity<Void> verifyCurrentPassword(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody CurrentPasswordRequest request
    ) {
        authService.verifyCurrentPassword(principal.id(), request.currentPassword());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/login-history")
    public List<LoginRecord> loginHistory(
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return authService.getRecentLogins(principal.id());
    }

    @PostMapping(
            value = "/avatar",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public UserProfile uploadAvatar(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestPart("file") MultipartFile file
    ) {
        return avatarService.upload(principal.id(), file);
    }

    @GetMapping("/avatar")
    public ResponseEntity<InputStreamResource> avatar(
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        AvatarDownload avatar = avatarService.download(principal.id());
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(avatar.contentType()))
                .cacheControl(org.springframework.http.CacheControl.noStore())
                .body(new InputStreamResource(avatar.inputStream()));
    }

    @DeleteMapping("/avatar")
    public UserProfile removeAvatar(
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return avatarService.remove(principal.id());
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        authService.logout((String) request.getAttribute("jwtTokenId"));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/password-reset/request")
    public ResponseEntity<PasswordResetResponse> requestPasswordReset(
            @Valid @RequestBody PasswordResetRequest request
    ) {
        return ResponseEntity.accepted().body(passwordResetService.request(request));
    }

    @PostMapping("/password-reset/confirm")
    public ResponseEntity<Void> confirmPasswordReset(
            @Valid @RequestBody PasswordResetConfirmRequest request
    ) {
        passwordResetService.confirm(request);
        return ResponseEntity.noContent().build();
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
