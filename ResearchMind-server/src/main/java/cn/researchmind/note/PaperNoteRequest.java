package cn.researchmind.note;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record PaperNoteRequest(
        @NotNull(message = "笔记内容不能为空")
        @Size(max = 20000, message = "笔记不能超过 20000 个字符")
        String content
) {
}
