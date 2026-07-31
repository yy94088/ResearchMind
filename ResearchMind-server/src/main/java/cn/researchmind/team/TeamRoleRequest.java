package cn.researchmind.team;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record TeamRoleRequest(
        @NotBlank(message = "团队角色不能为空")
        @Pattern(
                regexp = "^(MANAGER|MEMBER|GUEST)$",
                message = "团队角色只能是 MANAGER、MEMBER 或 GUEST"
        )
        String role
) {
}
