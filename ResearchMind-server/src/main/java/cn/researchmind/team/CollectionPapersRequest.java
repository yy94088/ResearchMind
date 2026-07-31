package cn.researchmind.team;

import java.util.List;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CollectionPapersRequest(
        @NotNull(message = "文献列表不能为空")
        @Size(max = 500, message = "单个专题一次最多管理 500 篇文献")
        List<String> paperIds
) {
}
