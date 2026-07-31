package cn.researchmind.paper;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PaperRequest(
        @NotBlank(message = "文献原始标题不能为空")
        @Size(max = 500, message = "文献原始标题不能超过 500 个字符")
        String title,

        @Size(max = 500, message = "中文标题不能超过 500 个字符")
        String titleZh,

        @JsonProperty("abstract")
        String abstractText,

        @Size(max = 150, message = "DOI 不能超过 150 个字符")
        String doi,

        @Size(max = 300, message = "期刊或会议名称不能超过 300 个字符")
        String journal,

        @Min(value = 1000, message = "发表年份不能早于 1000 年")
        @Max(value = 2100, message = "发表年份不能晚于 2100 年")
        Integer year,

        @Size(max = 20, message = "语言标识不能超过 20 个字符")
        String language,

        @Size(max = 500, message = "文件名不能超过 500 个字符")
        String fileName,

        @Min(value = 0, message = "页数不能为负数")
        Integer pages,

        @Size(max = 100, message = "作者不能超过 100 位")
        List<
                @NotBlank(message = "作者姓名不能为空")
                @Size(max = 200, message = "作者姓名不能超过 200 个字符")
                String
        > authors,

        @Size(max = 50, message = "标签不能超过 50 个")
        List<
                @NotBlank(message = "标签不能为空")
                @Size(max = 100, message = "标签不能超过 100 个字符")
                String
        > tags,

        @Size(max = 10, message = "关联研究领域不能超过 10 个")
        List<@Valid PaperAreaRequest> areas,

        @Size(max = 200, message = "研究领域不能超过 200 个字符")
        String area,

        @Size(max = 36, message = "上传记录编号格式不正确")
        String uploadId
) {
}
