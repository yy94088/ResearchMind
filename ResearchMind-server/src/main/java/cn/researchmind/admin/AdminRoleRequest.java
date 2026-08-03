package cn.researchmind.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record AdminRoleRequest(
        @NotBlank(message = "用户角色不能为空")
        @Pattern(regexp = "USER|MANAGER|ADMIN", message = "用户角色无效")
        String role
) {
}
