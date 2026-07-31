package cn.researchmind.paper;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record ProgressRequest(
        @Min(value = 1, message = "当前页码不能小于 1")
        @Max(value = 100000, message = "当前页码超出允许范围")
        int currentPage,
        @Min(value = 0, message = "本次阅读时长不能小于 0")
        @Max(value = 60, message = "单次上报阅读时长不能超过 60 秒")
        int readSeconds
) {
}
