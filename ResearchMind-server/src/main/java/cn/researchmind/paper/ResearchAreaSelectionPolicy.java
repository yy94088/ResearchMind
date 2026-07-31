package cn.researchmind.paper;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class ResearchAreaSelectionPolicy {

    public static final double PRIMARY_MIN_CONFIDENCE = 0.70;
    public static final double RELATED_MIN_CONFIDENCE = 0.80;
    public static final int MAX_RELATED_AREAS = 2;

    private ResearchAreaSelectionPolicy() {
    }

    public static List<PaperAreaView> select(
            List<ResearchAreaCandidate> candidates
    ) {
        return select(candidates, null);
    }

    public static List<PaperAreaView> select(
            List<ResearchAreaCandidate> candidates,
            String evidenceSource
    ) {
        List<NormalizedCandidate> valid = normalize(candidates).stream()
                .filter(candidate ->
                        hasEvidence(candidate.evidence(), evidenceSource)
                )
                .filter(candidate -> evidenceMatchesArea(
                        candidate.name(),
                        candidate.evidence()
                ))
                .toList();
        if (valid.isEmpty()) return List.of();

        NormalizedCandidate primary = valid.stream()
                .filter(NormalizedCandidate::primary)
                .max(Comparator.comparingDouble(
                        NormalizedCandidate::confidence
                ))
                .orElseGet(() -> valid.stream()
                        .max(Comparator.comparingDouble(
                                NormalizedCandidate::confidence
                        ))
                        .orElse(null));
        if (primary == null
                || primary.confidence() < PRIMARY_MIN_CONFIDENCE) {
            return List.of();
        }

        List<PaperAreaView> result = new ArrayList<>();
        result.add(new PaperAreaView(
                primary.name(),
                primary.confidence(),
                true
        ));
        valid.stream()
                .filter(candidate -> !candidate.name().equals(primary.name()))
                .filter(candidate -> !candidate.primary())
                .filter(candidate ->
                        candidate.confidence() >= RELATED_MIN_CONFIDENCE
                )
                .sorted(Comparator.comparingDouble(
                        NormalizedCandidate::confidence
                ).reversed())
                .limit(MAX_RELATED_AREAS)
                .map(candidate -> new PaperAreaView(
                        candidate.name(),
                        candidate.confidence(),
                        false
                ))
                .forEach(result::add);
        return List.copyOf(result);
    }

    private static List<NormalizedCandidate> normalize(
            List<ResearchAreaCandidate> candidates
    ) {
        if (candidates == null) return List.of();
        Map<String, NormalizedCandidate> unique = new LinkedHashMap<>();
        for (ResearchAreaCandidate candidate : candidates) {
            if (candidate == null || candidate.name() == null) continue;
            String name = candidate.name().trim();
            if (!ResearchAreaCatalog.SUPPORTED.contains(name)) continue;
            double confidence = candidate.confidence() == null
                    ? 0.0
                    : Math.max(0.0, Math.min(1.0, candidate.confidence()));
            String evidence = candidate.evidence() == null
                    ? ""
                    : candidate.evidence().trim();
            String key = name.toLowerCase(Locale.ROOT);
            NormalizedCandidate normalized = new NormalizedCandidate(
                    name,
                    confidence,
                    Boolean.TRUE.equals(candidate.primary()),
                    evidence
            );
            NormalizedCandidate existing = unique.get(key);
            if (existing == null) {
                unique.put(key, normalized);
            } else {
                boolean useNewEvidence = normalized.confidence()
                        > existing.confidence();
                unique.put(key, new NormalizedCandidate(
                        existing.name(),
                        Math.max(
                                existing.confidence(),
                                normalized.confidence()
                        ),
                        existing.primary() || normalized.primary(),
                        useNewEvidence
                                ? normalized.evidence()
                                : existing.evidence()
                ));
            }
        }
        return List.copyOf(unique.values());
    }

    private static boolean hasEvidence(
            String evidence,
            String evidenceSource
    ) {
        if (evidence == null
                || evidence.codePointCount(0, evidence.length()) < 4) {
            return false;
        }
        if (evidenceSource == null) return true;
        String normalizedEvidence = normalizeEvidenceText(evidence);
        String normalizedSource = normalizeEvidenceText(evidenceSource);
        return !normalizedEvidence.isBlank()
                && normalizedSource.contains(normalizedEvidence);
    }

    private static String normalizeEvidenceText(String value) {
        return value == null
                ? ""
                : value.toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static boolean evidenceMatchesArea(
            String areaName,
            String evidence
    ) {
        String value = normalizeEvidenceText(evidence);
        return switch (areaName) {
            case "自然语言处理" -> containsAny(
                    value,
                    "自然语言处理",
                    "自然语言理解",
                    "自然语言生成",
                    "文本分类",
                    "机器翻译",
                    "问答",
                    "信息抽取",
                    "情感分析",
                    "natural language processing",
                    "natural language understanding",
                    "natural language generation",
                    "text classification",
                    "machine translation",
                    "question answering",
                    "information extraction",
                    "sentiment analysis",
                    "nlp"
            );
            case "图神经网络" -> containsAny(
                    value,
                    "图神经网络",
                    "图表示学习",
                    "消息传递",
                    "graph neural network",
                    "graph representation learning",
                    "message passing",
                    "gnn"
            );
            case "计算机视觉" -> containsAny(
                    value,
                    "计算机视觉",
                    "图像分类",
                    "目标检测",
                    "图像分割",
                    "视频理解",
                    "computer vision",
                    "image classification",
                    "object detection",
                    "image segmentation",
                    "video understanding"
            );
            case "大语言模型" -> containsAny(
                    value,
                    "大语言模型",
                    "大型语言模型",
                    "指令微调",
                    "提示学习",
                    "large language model",
                    "instruction tuning",
                    "prompt tuning",
                    "llm"
            );
            case "时间序列" -> containsAny(
                    value,
                    "时间序列",
                    "时序预测",
                    "序列预测",
                    "time series",
                    "temporal forecasting",
                    "sequence forecasting"
            );
            case "可信人工智能" -> containsAny(
                    value,
                    "可信人工智能",
                    "可解释性",
                    "模型可信",
                    "鲁棒性",
                    "公平性",
                    "trustworthy ai",
                    "explainability",
                    "interpretability",
                    "robustness",
                    "fairness"
            );
            case "隐私计算" -> containsAny(
                    value,
                    "隐私计算",
                    "隐私保护",
                    "联邦学习",
                    "差分隐私",
                    "安全多方计算",
                    "privacy computing",
                    "privacy-preserving",
                    "federated learning",
                    "differential privacy",
                    "secure multi-party computation"
            );
            default -> false;
        };
    }

    private static boolean containsAny(String value, String... markers) {
        for (String marker : markers) {
            if (value.contains(marker)) return true;
        }
        return false;
    }

    private record NormalizedCandidate(
            String name,
            double confidence,
            boolean primary,
            String evidence
    ) {
    }
}
