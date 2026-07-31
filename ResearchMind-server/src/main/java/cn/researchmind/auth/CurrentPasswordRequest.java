package cn.researchmind.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CurrentPasswordRequest(
        @NotBlank(message = "当前密码不能为空")
        @Size(max = 72, message = "当前密码不能超过 72 个字符")
        String currentPassword
) {
}
