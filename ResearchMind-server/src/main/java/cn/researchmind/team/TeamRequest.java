package cn.researchmind.team;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TeamRequest(
        @NotBlank(message = "团队名称不能为空")
        @Size(max = 100, message = "团队名称不能超过 100 个字符")
        String name,

        @Size(max = 500, message = "团队简介不能超过 500 个字符")
        String description,

        @Size(max = 200, message = "所属机构不能超过 200 个字符")
        String institution
) {
}
