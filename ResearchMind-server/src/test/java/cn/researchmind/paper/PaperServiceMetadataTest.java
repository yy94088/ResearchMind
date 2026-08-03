package cn.researchmind.paper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import cn.researchmind.activity.OperationLogService;
import cn.researchmind.common.ApiException;
import cn.researchmind.storage.ObjectStorageService;
import cn.researchmind.upload.UploadService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PaperServiceMetadataTest {

    @Mock private PaperRepository paperRepository;
    @Mock private UploadService uploadService;
    @Mock private ObjectStorageService objectStorageService;
    @Mock private OperationLogService operationLogService;

    private PaperService paperService;

    @BeforeEach
    void setUp() {
        paperService = new PaperService(
                paperRepository,
                uploadService,
                objectStorageService,
                operationLogService
        );
    }

    @Test
    void shouldCalculateProgressFromRenderedPdfPage() {
        PaperView paper = paper(
                "",
                List.of(),
                2026,
                "",
                "",
                "未分类",
                List.of(),
                ""
        );
        when(paperRepository.findById("user-1", "paper-1"))
                .thenReturn(Optional.of(paper));

        paperService.setProgress(
                "user-1",
                "paper-1",
                new ProgressRequest(3, 15)
        );

        verify(paperRepository).setReadingPage(
                "user-1",
                "paper-1",
                3,
                30,
                15
        );
    }

    @Test
    void shouldRejectReadingPageAfterPdfEnd() {
        PaperView paper = paper(
                "",
                List.of(),
                2026,
                "",
                "",
                "未分类",
                List.of(),
                ""
        );
        when(paperRepository.findById("user-1", "paper-1"))
                .thenReturn(Optional.of(paper));

        assertThatThrownBy(() -> paperService.setProgress(
                "user-1",
                "paper-1",
                new ProgressRequest(11, 0)
        ))
                .isInstanceOf(ApiException.class)
                .hasMessage("当前页码不能大于 PDF 总页数");
        verify(paperRepository, never()).setReadingPage(
                any(),
                any(),
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyInt()
        );
    }

    @Test
    void shouldFillOnlyMissingMetadataFromAiAnalysis() {
        PaperView missing = paper(
                "",
                List.of(),
                null,
                "",
                "",
                "未分类",
                List.of(),
                ""
        );
        PaperView updated = paper(
                "图学习论文",
                List.of("Alice"),
                2026,
                "AI Conference",
                "10.1000/ai",
                "图神经网络",
                List.of("图学习"),
                "论文摘要"
        );
        when(paperRepository.findById("user-1", "paper-1"))
                .thenReturn(Optional.of(missing))
                .thenReturn(Optional.of(updated));
        when(paperRepository.doiExists("user-1", "10.1000/ai", "paper-1"))
                .thenReturn(false);

        PaperMetadataFillResult result = paperService.fillMissingMetadata(
                "user-1",
                "paper-1",
                new PaperMetadataCompletion(
                        "图学习论文",
                        List.of("Alice"),
                        List.of("图学习"),
                        "论文摘要",
                        "10.1000/ai",
                        2026,
                        "AI Conference",
                        List.of(new PaperAreaView(
                                "图神经网络",
                                0.94,
                                true
                        ))
                )
        );

        assertThat(result.paper()).isEqualTo(updated);
        assertThat(result.filledFields()).containsExactly(
                "中文标题",
                "作者",
                "关键词",
                "摘要",
                "DOI",
                "发表年份",
                "期刊 / 会议",
                "研究领域"
        );
        verify(paperRepository).updateMissingScalarMetadata(
                org.mockito.ArgumentMatchers.eq("user-1"),
                org.mockito.ArgumentMatchers.eq("paper-1"),
                any(PaperMetadataCompletion.class)
        );
        verify(paperRepository).addAuthors("paper-1", List.of("Alice"));
        verify(paperRepository).addTags("user-1", "paper-1", List.of("图学习"));
        verify(paperRepository).replaceAreas(
                "paper-1",
                List.of(new PaperAreaView("图神经网络", 0.94, true))
        );
    }

    @Test
    void shouldNotOverwriteExistingMetadata() {
        PaperView complete = paper(
                "已有中文标题",
                List.of("Existing Author"),
                2025,
                "Existing Journal",
                "10.1000/existing",
                "计算机视觉",
                List.of("视觉"),
                "已有摘要"
        );
        when(paperRepository.findById("user-1", "paper-1"))
                .thenReturn(Optional.of(complete));

        PaperMetadataFillResult result = paperService.fillMissingMetadata(
                "user-1",
                "paper-1",
                new PaperMetadataCompletion(
                        "AI 标题",
                        List.of("AI Author"),
                        List.of("AI Keyword"),
                        "AI 摘要",
                        "10.1000/ai",
                        2026,
                        "AI Journal",
                        List.of(new PaperAreaView(
                                "计算机视觉",
                                0.91,
                                true
                        ))
                )
        );

        assertThat(result.paper()).isEqualTo(complete);
        assertThat(result.filledFields()).isEmpty();
        verify(paperRepository, never()).updateMissingScalarMetadata(
                any(),
                any(),
                any()
        );
        verify(operationLogService, never()).record(
                any(),
                any(),
                any(),
                any(),
                any(),
                any()
        );
    }

    @Test
    void shouldKeepExistingPrimaryAreaAndAddAiRelatedAreas() {
        PaperView current = paper(
                "已有中文标题",
                List.of("Existing Author"),
                2025,
                "Existing Journal",
                "10.1000/existing",
                "计算机视觉",
                List.of("视觉"),
                "已有摘要"
        );
        List<PaperAreaView> mergedAreas = List.of(
                new PaperAreaView("计算机视觉", 1.0, true),
                new PaperAreaView("图神经网络", 0.86, false)
        );
        PaperView updated = new PaperView(
                current.id(),
                current.title(),
                current.titleZh(),
                current.authors(),
                current.institutions(),
                current.year(),
                current.journal(),
                current.doi(),
                current.area(),
                mergedAreas,
                current.tags(),
                current.abstractText(),
                current.favorite(),
                current.read(),
                current.progress(),
                current.currentPage(),
                current.totalReadSeconds(),
                current.pages(),
                current.fileName(),
                current.fileAvailable(),
                current.uploadDate(),
                current.lastReadTime()
        );
        when(paperRepository.findById("user-1", "paper-1"))
                .thenReturn(Optional.of(current))
                .thenReturn(Optional.of(updated));

        PaperMetadataFillResult result = paperService.fillMissingMetadata(
                "user-1",
                "paper-1",
                new PaperMetadataCompletion(
                        "",
                        List.of(),
                        List.of(),
                        "",
                        "",
                        null,
                        "",
                        List.of(new PaperAreaView(
                                "图神经网络",
                                0.86,
                                true
                        ))
                )
        );

        assertThat(result.paper().areas()).isEqualTo(mergedAreas);
        assertThat(result.filledFields()).containsExactly("研究领域");
        verify(paperRepository).replaceAreas("paper-1", mergedAreas);
    }

    @Test
    void shouldFillOtherFieldsWhenAiCannotClassifyArea() {
        PaperView missing = paper(
                "",
                List.of(),
                null,
                "",
                "",
                "未分类",
                List.of(),
                ""
        );
        PaperView updated = paper(
                "中文标题",
                List.of("Alice"),
                null,
                "",
                "",
                "未分类",
                List.of("知识库"),
                ""
        );
        when(paperRepository.findById("user-1", "paper-1"))
                .thenReturn(Optional.of(missing))
                .thenReturn(Optional.of(updated));

        PaperMetadataFillResult result = paperService.fillMissingMetadata(
                "user-1",
                "paper-1",
                new PaperMetadataCompletion(
                        "中文标题",
                        List.of("Alice"),
                        List.of("知识库"),
                        "",
                        "",
                        null,
                        "",
                        List.of()
                )
        );

        assertThat(result.filledFields())
                .containsExactly("中文标题", "作者", "关键词")
                .doesNotContain("研究领域");
        verify(paperRepository, never()).replaceAreas(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyList()
        );
    }

    private PaperView paper(
            String titleZh,
            List<String> authors,
            Integer year,
            String journal,
            String doi,
            String area,
            List<String> tags,
            String abstractText
    ) {
        return new PaperView(
                "paper-1",
                "Paper Title",
                titleZh,
                authors,
                List.of(),
                year,
                journal,
                doi,
                area,
                List.of(new PaperAreaView(area, 1.0, true)),
                tags,
                abstractText,
                false,
                false,
                0,
                0,
                0,
                10,
                "paper.pdf",
                true,
                LocalDate.of(2026, 7, 31),
                null
        );
    }
}
