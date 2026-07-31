package cn.researchmind.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PasswordResetConfirmRequest(
        @NotBlank(message = "重置令牌不能为空")
        @Size(max = 200, message = "重置令牌格式不正确")
        String token,

        @NotBlank(message = "新密码不能为空")
        @Size(min = 8, max = 72, message = "新密码长度应为 8–72 个字符")
        String newPassword
) {
}
