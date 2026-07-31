package cn.researchmind.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @NotBlank(message = "用户名或邮箱不能为空")
        @Size(max = 100, message = "登录账号不能超过 100 个字符")
        String account,

        @NotBlank(message = "密码不能为空")
        @Size(max = 72, message = "密码不能超过 72 个字符")
        String password,

        boolean rememberMe
) {
}
