package cn.researchmind.team;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CollectionRequest(
        @NotBlank(message = "专题名称不能为空")
        @Size(max = 150, message = "专题名称不能超过 150 个字符")
        String name,

        @Size(max = 500, message = "专题简介不能超过 500 个字符")
        String description,

        @Pattern(
                regexp = "^#[0-9a-fA-F]{6}$",
                message = "专题颜色必须是六位十六进制颜色"
        )
        String color
) {
}
