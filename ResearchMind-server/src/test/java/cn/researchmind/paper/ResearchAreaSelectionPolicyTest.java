package cn.researchmind.paper;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class ResearchAreaSelectionPolicyTest {

    @Test
    void shouldNotRequireRelatedArea() {
        List<PaperAreaView> result = ResearchAreaSelectionPolicy.select(List.of(
                candidate("图神经网络", 0.92, true, "核心方法采用图神经网络"),
                candidate("大语言模型", 0.79, false, "正文仅将其作为对比模型")
        ));

        assertThat(result).containsExactly(
                new PaperAreaView("图神经网络", 0.92, true)
        );
    }

    @Test
    void shouldRejectAreaWithoutEvidence() {
        List<PaperAreaView> result = ResearchAreaSelectionPolicy.select(List.of(
                candidate("自然语言处理", 0.95, true, "")
        ));

        assertThat(result).isEmpty();
    }

    @Test
    void shouldKeepAtMostTwoStrongEvidenceBasedRelatedAreas() {
        List<PaperAreaView> result = ResearchAreaSelectionPolicy.select(List.of(
                candidate("图神经网络", 0.95, true, "核心贡献是图表示学习"),
                candidate("大语言模型", 0.90, false, "核心模型包含大语言模型"),
                candidate("自然语言处理", 0.88, false, "主要任务是自然语言理解"),
                candidate("可信人工智能", 0.86, false, "主要评测目标是模型可信性")
        ));

        assertThat(result).containsExactly(
                new PaperAreaView("图神经网络", 0.95, true),
                new PaperAreaView("大语言模型", 0.90, false),
                new PaperAreaView("自然语言处理", 0.88, false)
        );
    }

    @Test
    void shouldRejectEvidenceThatDoesNotOccurInPaperText() {
        List<PaperAreaView> result = ResearchAreaSelectionPolicy.select(
                List.of(candidate(
                        "自然语言处理",
                        0.95,
                        true,
                        "论文核心任务是机器翻译"
                )),
                "本文研究图结构上的节点分类，不涉及机器翻译任务。"
        );

        assertThat(result).isEmpty();
    }

    @Test
    void shouldNotInferNaturalLanguageProcessingFromLlmMention() {
        String source = """
                Graph Neural Networks (GNNs) have evolved to understand graph
                structures. Inspired by the success of large language models
                (LLMs), we introduce a graph-oriented LLM.
                """;
        List<PaperAreaView> result = ResearchAreaSelectionPolicy.select(
                List.of(
                        candidate(
                                "自然语言处理",
                                0.95,
                                true,
                                "Inspired by the success of large language models"
                        ),
                        candidate(
                                "图神经网络",
                                0.90,
                                false,
                                "Graph Neural Networks (GNNs)"
                        ),
                        candidate(
                                "大语言模型",
                                0.85,
                                false,
                                "large language models"
                        )
                ),
                source
        );

        assertThat(result).containsExactly(
                new PaperAreaView("图神经网络", 0.90, true),
                new PaperAreaView("大语言模型", 0.85, false)
        );
    }

    private ResearchAreaCandidate candidate(
            String name,
            double confidence,
            boolean primary,
            String evidence
    ) {
        return new ResearchAreaCandidate(
                name,
                confidence,
                primary,
                evidence
        );
    }
}
