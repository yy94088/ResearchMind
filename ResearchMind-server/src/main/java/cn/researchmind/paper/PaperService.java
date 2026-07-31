package cn.researchmind.paper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import cn.researchmind.activity.OperationLogService;
import cn.researchmind.common.ApiException;
import cn.researchmind.storage.ObjectStorageService;
import cn.researchmind.upload.UploadArtifact;
import cn.researchmind.upload.UploadService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaperService {

    private final PaperRepository paperRepository;
    private final UploadService uploadService;
    private final ObjectStorageService objectStorageService;
    private final OperationLogService operationLogService;

    public PaperService(
            PaperRepository paperRepository,
            UploadService uploadService,
            ObjectStorageService objectStorageService,
            OperationLogService operationLogService
    ) {
        this.paperRepository = paperRepository;
        this.uploadService = uploadService;
        this.objectStorageService = objectStorageService;
        this.operationLogService = operationLogService;
    }

    public List<PaperView> findAll(String ownerId) {
        return paperRepository.findAll(ownerId);
    }

    public PaperView findById(String ownerId, String paperId) {
        return requirePaper(ownerId, paperId);
    }

    @Transactional
    public PaperMetadataFillResult fillMissingMetadata(
            String ownerId,
            String paperId,
            PaperMetadataCompletion completion
    ) {
        PaperView current = requirePaper(ownerId, paperId);
        if (completion == null) {
            return new PaperMetadataFillResult(current, List.of());
        }

        List<String> fields = new ArrayList<>();
        String titleZh = missing(current.titleZh())
                ? value(completion.titleZh(), 500)
                : null;
        if (titleZh != null) fields.add("中文标题");

        List<String> authors = current.authors().isEmpty()
                ? values(completion.authors(), 100, 200)
                : List.of();
        if (!authors.isEmpty()) fields.add("作者");

        List<String> keywords = current.tags().isEmpty()
                ? values(completion.keywords(), 50, 100)
                : List.of();
        if (!keywords.isEmpty()) fields.add("关键词");

        String abstractText = missing(current.abstractText())
                ? value(completion.abstractText(), 12_000)
                : null;
        if (abstractText != null) fields.add("摘要");

        String doi = missing(current.doi())
                ? value(completion.doi(), 150)
                : null;
        if (doi != null && paperRepository.doiExists(ownerId, doi, paperId)) {
            doi = null;
        }
        if (doi != null) fields.add("DOI");

        Integer year = current.year() == null && validYear(completion.year())
                ? completion.year()
                : null;
        if (year != null) fields.add("发表年份");

        String journal = missing(current.journal())
                ? value(completion.journal(), 300)
                : null;
        if (journal != null) fields.add("期刊 / 会议");

        List<PaperAreaView> areas = mergeAreas(
                current.areas(),
                completion.areas()
        );
        if (areas != null) fields.add("研究领域");

        if (fields.isEmpty()) {
            return new PaperMetadataFillResult(current, List.of());
        }

        PaperMetadataCompletion accepted = new PaperMetadataCompletion(
                titleZh,
                authors,
                keywords,
                abstractText,
                doi,
                year,
                journal,
                areas == null ? List.of() : areas
        );
        paperRepository.updateMissingScalarMetadata(ownerId, paperId, accepted);
        if (!authors.isEmpty()) paperRepository.addAuthors(paperId, authors);
        if (!keywords.isEmpty()) paperRepository.addTags(ownerId, paperId, keywords);
        if (areas != null) paperRepository.replaceAreas(paperId, areas);

        PaperView updated = requirePaper(ownerId, paperId);
        operationLogService.record(
                ownerId,
                "AI",
                "AI 深度解读补全了文献元数据",
                "PAPER",
                paperId,
                String.join("、", fields)
        );
        return new PaperMetadataFillResult(updated, List.copyOf(fields));
    }

    @Transactional
    public PaperView create(String ownerId, PaperRequest request) {
        ensureDoiAvailable(ownerId, normalizeDoi(request.doi()), null);
        String uploadId = normalizeOptional(request.uploadId());
        UploadArtifact uploadedFile = uploadId == null
                ? null
                : uploadService.requireAvailable(ownerId, uploadId);
        String paperId = paperRepository.insert(ownerId, request, uploadedFile);
        if (uploadedFile != null) {
            uploadService.attachToPaper(ownerId, uploadId, paperId);
        }
        PaperView created = requirePaper(ownerId, paperId);
        operationLogService.record(
                ownerId,
                "PAPER",
                "导入了 1 篇文献",
                "PAPER",
                paperId,
                displayTitle(created)
        );
        return created;
    }

    @Transactional
    public PaperView update(String ownerId, String paperId, PaperRequest request) {
        requirePaper(ownerId, paperId);
        ensureDoiAvailable(ownerId, normalizeDoi(request.doi()), paperId);
        if (paperRepository.update(ownerId, paperId, request) == 0) {
            throw notFound();
        }
        return requirePaper(ownerId, paperId);
    }

    @Transactional
    public void delete(String ownerId, String paperId) {
        PaperView paper = requirePaper(ownerId, paperId);
        StoredPaperFile storedFile = paperRepository.findStoredFile(ownerId, paperId)
                .orElse(null);
        if (paperRepository.softDelete(ownerId, paperId) == 0) {
            throw notFound();
        }
        if (storedFile != null) {
            objectStorageService.removeQuietly(storedFile.objectKey());
        }
        operationLogService.record(
                ownerId,
                "DELETE",
                "移除了 1 篇文献",
                "PAPER",
                paperId,
                displayTitle(paper)
        );
    }

    @Transactional
    public PaperView setFavorite(
            String ownerId,
            String paperId,
            FavoriteRequest request
    ) {
        requirePaper(ownerId, paperId);
        paperRepository.setFavorite(ownerId, paperId, request.favorite());
        return requirePaper(ownerId, paperId);
    }

    @Transactional
    public PaperView setProgress(
            String ownerId,
            String paperId,
            ProgressRequest request
    ) {
        PaperView paper = requirePaper(ownerId, paperId);
        if (!paper.fileAvailable()) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "PAPER_FILE_NOT_AVAILABLE",
                    "该文献没有可用于阅读的 PDF 原文"
            );
        }
        if (paper.pages() == null || paper.pages() < 1) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "PAPER_PAGE_COUNT_MISSING",
                    "PDF 总页数缺失，无法计算阅读进度"
            );
        }
        if (request.currentPage() > paper.pages()) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_READING_PAGE",
                    "当前页码不能大于 PDF 总页数"
            );
        }
        int progress = (int) Math.ceil(
                request.currentPage() * 100.0 / paper.pages()
        );
        paperRepository.setReadingPage(
                ownerId,
                paperId,
                request.currentPage(),
                progress,
                request.readSeconds()
        );
        return requirePaper(ownerId, paperId);
    }

    public PaperFileDownload openFile(String ownerId, String paperId) {
        requirePaper(ownerId, paperId);
        StoredPaperFile file = paperRepository.findStoredFile(ownerId, paperId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "PAPER_FILE_NOT_FOUND",
                        "该文献没有可用的 PDF 原文"
                ));
        return new PaperFileDownload(
                objectStorageService.get(file.objectKey()),
                file.fileName(),
                file.fileSize()
        );
    }

    private PaperView requirePaper(String ownerId, String paperId) {
        return paperRepository.findById(ownerId, paperId).orElseThrow(this::notFound);
    }

    private String displayTitle(PaperView paper) {
        if (paper.title() != null && !paper.title().isBlank()) return paper.title();
        return paper.titleZh();
    }

    private void ensureDoiAvailable(String ownerId, String doi, String excludedPaperId) {
        if (paperRepository.doiExists(ownerId, doi, excludedPaperId)) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "PAPER_DOI_ALREADY_EXISTS",
                    "你的文献库中已存在相同 DOI 的文献"
            );
        }
    }

    private String normalizeDoi(String doi) {
        return normalizeOptional(doi);
    }

    private String normalizeOptional(String value) {
        if (value == null || value.isBlank()) return null;
        return value.trim();
    }

    private String value(String source, int maximumLength) {
        String normalized = normalizeOptional(source);
        if (normalized == null) return null;
        return normalized.length() <= maximumLength
                ? normalized
                : normalized.substring(0, maximumLength);
    }

    private List<String> values(
            List<String> source,
            int maximumItems,
            int maximumLength
    ) {
        if (source == null) return List.of();
        Map<String, String> unique = new LinkedHashMap<>();
        for (String item : source) {
            String normalized = value(item, maximumLength);
            if (normalized == null) continue;
            unique.putIfAbsent(normalized.toLowerCase(Locale.ROOT), normalized);
            if (unique.size() == maximumItems) break;
        }
        return List.copyOf(unique.values());
    }

    private boolean missing(String value) {
        return value == null || value.isBlank();
    }

    private boolean validYear(Integer year) {
        return year != null && year >= 1000 && year <= 2100;
    }

    private List<PaperAreaView> mergeAreas(
            List<PaperAreaView> currentAreas,
            List<PaperAreaView> suggestedAreas
    ) {
        List<PaperAreaView> suggestions = normalizeSuggestedAreas(suggestedAreas);
        if (suggestions.isEmpty()) return null;

        List<PaperAreaView> current = currentAreas == null
                ? List.of()
                : currentAreas.stream()
                .filter(area -> area != null && !missing(area.name()))
                .toList();
        boolean onlyUnclassified = current.isEmpty()
                || current.stream().allMatch(
                        area -> "未分类".equals(area.name().trim())
                );

        List<PaperAreaView> merged = new ArrayList<>();
        if (!onlyUnclassified) {
            merged.addAll(current);
        }
        for (PaperAreaView suggestion : suggestions) {
            boolean exists = merged.stream().anyMatch(
                    area -> area.name().equalsIgnoreCase(suggestion.name())
            );
            if (exists || merged.size() == 10) continue;
            merged.add(new PaperAreaView(
                    suggestion.name(),
                    suggestion.confidence(),
                    onlyUnclassified && merged.isEmpty()
                            ? true
                            : false
            ));
        }
        if (merged.isEmpty()) return null;

        boolean hasPrimary = merged.stream().anyMatch(PaperAreaView::primary);
        if (!hasPrimary) {
            PaperAreaView first = merged.get(0);
            merged.set(0, new PaperAreaView(
                    first.name(),
                    first.confidence(),
                    true
            ));
        }
        List<PaperAreaView> normalized = new ArrayList<>();
        boolean primaryAssigned = false;
        for (PaperAreaView area : merged) {
            boolean primary = area.primary() && !primaryAssigned;
            primaryAssigned = primaryAssigned || primary;
            normalized.add(new PaperAreaView(
                    area.name(),
                    Math.max(0.0, Math.min(1.0, area.confidence())),
                    primary
            ));
        }
        List<PaperAreaView> result = List.copyOf(normalized);
        return result.equals(current) ? null : result;
    }

    private List<PaperAreaView> normalizeSuggestedAreas(
            List<PaperAreaView> suggestedAreas
    ) {
        if (suggestedAreas == null) return List.of();
        Map<String, PaperAreaView> unique = new LinkedHashMap<>();
        for (PaperAreaView area : suggestedAreas) {
            if (area == null) continue;
            String name = value(area.name(), 200);
            if (name == null || !ResearchAreaCatalog.SUPPORTED.contains(name)) {
                continue;
            }
            double confidence = Math.max(
                    0.0,
                    Math.min(1.0, area.confidence())
            );
            String key = name.toLowerCase(Locale.ROOT);
            PaperAreaView existing = unique.get(key);
            if (existing == null || confidence > existing.confidence()) {
                unique.put(key, new PaperAreaView(
                        name,
                        confidence,
                        area.primary()
                ));
            }
            if (unique.size() == 10) break;
        }
        List<PaperAreaView> result = new ArrayList<>(unique.values());
        result.sort((left, right) -> {
            if (left.primary() != right.primary()) {
                return left.primary() ? -1 : 1;
            }
            return Double.compare(right.confidence(), left.confidence());
        });
        return List.copyOf(result);
    }

    private ApiException notFound() {
        return new ApiException(
                HttpStatus.NOT_FOUND,
                "PAPER_NOT_FOUND",
                "文献不存在或不属于当前用户"
        );
    }
}
