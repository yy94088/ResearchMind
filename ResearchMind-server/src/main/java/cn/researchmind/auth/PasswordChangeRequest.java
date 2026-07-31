package cn.researchmind.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PasswordChangeRequest(
        @NotBlank(message = "当前密码不能为空")
        @Size(max = 72, message = "当前密码不能超过 72 个字符")
        String currentPassword,

        @NotBlank(message = "新密码不能为空")
        @Size(min = 8, max = 72, message = "新密码长度应为 8–72 个字符")
        String newPassword
) {
}
