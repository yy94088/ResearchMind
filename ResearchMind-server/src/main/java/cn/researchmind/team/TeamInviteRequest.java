package cn.researchmind.team;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record TeamInviteRequest(
        @NotBlank(message = "邮箱不能为空")
        @Email(message = "邮箱格式不正确")
        @Size(max = 100, message = "邮箱不能超过 100 个字符")
        String email,

        @NotBlank(message = "团队角色不能为空")
        @Pattern(
                regexp = "^(MANAGER|MEMBER|GUEST)$",
                message = "团队角色只能是 MANAGER、MEMBER 或 GUEST"
        )
        String role
) {
}
