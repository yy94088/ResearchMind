package cn.researchmind.upload;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import cn.researchmind.ai.DeepSeekClient;
import cn.researchmind.ai.DeepSeekCompletion;
import cn.researchmind.ai.ModelTextSanitizer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PaperMetadataEnrichmentServiceTest {

    @Mock private DeepSeekClient deepSeekClient;
    @Mock private PdfParser pdfParser;

    private PaperMetadataEnrichmentService enrichmentService;

    @BeforeEach
    void setUp() {
        enrichmentService = new PaperMetadataEnrichmentService(
                deepSeekClient,
                pdfParser,
                new ModelTextSanitizer(),
                new ObjectMapper()
        );
    }

    @Test
    void shouldOnlyFillMissingMetadataAndKeepReliablePdfValues() throws Exception {
        ParsedPdf parsed = new ParsedPdf(
                "Reliable Local Title",
                List.of("Alice"),
                List.of("Graph Learning"),
                "",
                "10.1000/local",
                null,
                8
        );
        when(deepSeekClient.isConfigured()).thenReturn(true);
        when(pdfParser.extractText(new byte[]{1, 2}, 12, 24_000))
                .thenReturn("""
                        The core method is a graph neural network.
                        The model integrates a large language model.
                        """);
        when(deepSeekClient.completeJson(anyString(), anyString(), anyString()))
                .thenReturn(new DeepSeekCompletion("""
                        {
                          "title": "AI Replacement",
                          "titleZh": "可靠的本地标题",
                          "authors": ["Wrong Author"],
                          "keywords": ["Wrong Keyword"],
                          "abstract": "论文摘要",
                          "doi": "10.1000/wrong",
                          "year": 2026,
                          "journal": "AI Conference",
                          "areas": [
                            {"name": "图神经网络", "confidence": 0.96, "primary": true, "evidence": "The core method is a graph neural network."},
                            {"name": "大语言模型", "confidence": 0.83, "primary": false, "evidence": "The model integrates a large language model."}
                          ]
                        }
                        """, "deepseek-test", 100));

        EnrichedPaperMetadata result = enrichmentService.enrich(
                "user-1",
                new byte[]{1, 2},
                "paper.pdf",
                parsed,
                true
        );

        assertThat(result.title()).isEqualTo("Reliable Local Title");
        assertThat(result.authors()).containsExactly("Alice");
        assertThat(result.keywords()).containsExactly("Graph Learning");
        assertThat(result.doi()).isEqualTo("10.1000/local");
        assertThat(result.titleZh()).isEqualTo("可靠的本地标题");
        assertThat(result.abstractText()).isEqualTo("论文摘要");
        assertThat(result.year()).isEqualTo(2026);
        assertThat(result.journal()).isEqualTo("AI Conference");
        assertThat(result.area()).isEqualTo("图神经网络");
        assertThat(result.areas()).containsExactly(
                new cn.researchmind.paper.PaperAreaView(
                        "图神经网络",
                        0.96,
                        true
                ),
                new cn.researchmind.paper.PaperAreaView(
                        "大语言模型",
                        0.83,
                        false
                )
        );
        assertThat(result.aiEnrichedFields())
                .contains("中文标题", "摘要", "发表年份", "期刊 / 会议", "研究领域")
                .doesNotContain("作者", "关键词", "DOI", "原始标题");
    }

    @Test
    void shouldKeepLocalResultWhenAiIsNotConfigured() {
        ParsedPdf parsed = new ParsedPdf(
                "Local Title",
                List.of(),
                List.of(),
                "",
                "",
                null,
                3
        );
        when(deepSeekClient.isConfigured()).thenReturn(false);

        EnrichedPaperMetadata result = enrichmentService.enrich(
                "user-1",
                new byte[]{1},
                "local.pdf",
                parsed,
                true
        );

        assertThat(result.title()).isEqualTo("Local Title");
        assertThat(result.authors()).isEmpty();
        assertThat(result.keywords()).isEmpty();
        assertThat(result.area()).isEqualTo("未分类");
        assertThat(result.aiEnriched()).isFalse();
        assertThat(result.aiWarning()).contains("未配置 AI");
        verify(deepSeekClient).isConfigured();
    }

    @Test
    void shouldRejectAreaOutsideSupportedTaxonomy() throws Exception {
        ParsedPdf parsed = new ParsedPdf(
                "Local Title",
                List.of(),
                List.of(),
                "",
                "",
                null,
                3
        );
        when(deepSeekClient.isConfigured()).thenReturn(true);
        when(pdfParser.extractText(new byte[]{1}, 12, 24_000)).thenReturn("body");
        when(deepSeekClient.completeJson(anyString(), anyString(), anyString()))
                .thenReturn(new DeepSeekCompletion("""
                        {
                          "titleZh": "本地标题",
                          "authors": [],
                          "keywords": ["元数据"],
                          "areas": [
                            {"name": "量子物理", "confidence": 0.95, "primary": true}
                          ]
                        }
                        """, "deepseek-test", 50));

        EnrichedPaperMetadata result = enrichmentService.enrich(
                "user-1",
                new byte[]{1},
                "local.pdf",
                parsed,
                true
        );

        assertThat(result.area()).isEqualTo("未分类");
        assertThat(result.keywords()).isEmpty();
        assertThat(result.aiEnrichedFields()).doesNotContain("研究领域");
    }

    @Test
    void shouldSkipAiWhenUserDisablesEnrichment() {
        ParsedPdf parsed = new ParsedPdf(
                "Local Title",
                List.of(),
                List.of(),
                "",
                "",
                null,
                3
        );

        EnrichedPaperMetadata result = enrichmentService.enrich(
                "user-1",
                new byte[]{1},
                "local.pdf",
                parsed,
                false
        );

        assertThat(result.aiEnriched()).isFalse();
        assertThat(result.aiWarning()).isEmpty();
        assertThat(result.title()).isEqualTo("Local Title");
    }
}
