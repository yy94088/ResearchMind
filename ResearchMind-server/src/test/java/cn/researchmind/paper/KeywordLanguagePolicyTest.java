package cn.researchmind.paper;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class KeywordLanguagePolicyTest {

    @Test
    void shouldKeepOnlyEnglishGeneratedKeywordsForEnglishPaper() {
        List<String> result = KeywordLanguagePolicy.filterGeneratedKeywords(
                "Graph Neural Networks for Document Understanding",
                "We propose a message-passing architecture.",
                List.of(
                        "图神经网络",
                        "graph neural networks",
                        "message passing",
                        "GNN"
                )
        );

        assertThat(result).containsExactly(
                "graph neural networks",
                "message passing",
                "GNN"
        );
    }

    @Test
    void shouldNotTranslateOrDiscardKeywordsExtractedForChinesePaper() {
        List<String> result = KeywordLanguagePolicy.filterGeneratedKeywords(
                "面向文档理解的图神经网络研究",
                "本文提出一种新的消息传递架构。",
                List.of("图神经网络", "消息传递", "GNN")
        );

        assertThat(result).containsExactly("图神经网络", "消息传递", "GNN");
    }
}
