package cn.researchmind.paper;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PaperAreaRequest(
        @NotBlank(message = "研究领域名称不能为空")
        @Size(max = 200, message = "研究领域不能超过 200 个字符")
        String name,

        @DecimalMin(value = "0.0", message = "领域置信度不能小于 0")
        @DecimalMax(value = "1.0", message = "领域置信度不能大于 1")
        Double confidence,

        boolean primary
) {
}
