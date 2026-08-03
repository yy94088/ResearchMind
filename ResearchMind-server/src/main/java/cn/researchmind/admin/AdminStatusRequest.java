package cn.researchmind.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record AdminStatusRequest(
        @NotBlank(message = "账户状态不能为空")
        @Pattern(regexp = "ACTIVE|DISABLED", message = "账户状态无效")
        String status
) {
}
