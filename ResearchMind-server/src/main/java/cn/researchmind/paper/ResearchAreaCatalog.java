package cn.researchmind.paper;

import java.util.List;

public final class ResearchAreaCatalog {

    public static final List<String> SUPPORTED = List.of(
            "自然语言处理",
            "图神经网络",
            "计算机视觉",
            "大语言模型",
            "时间序列",
            "可信人工智能",
            "隐私计算"
    );

    private ResearchAreaCatalog() {
    }
}
