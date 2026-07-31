package cn.researchmind.upload;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

@Component
public class PdfParser {

    private static final Pattern DOI_PATTERN = Pattern.compile(
            "(?i)\\b10\\.\\d{4,9}/[-._;()/:A-Z0-9]+"
    );
    private static final Pattern ABSTRACT_PATTERN = Pattern.compile(
            "(?is)(?:^|\\n)\\s*(?:abstract|摘\\s*要)\\s*[:：]?\\s*(.{40,2500}?)"
                    + "(?=\\n\\s*(?:keywords?|key\\s*words|关键词|引言|"
                    + "(?:1|I)\\.?\\s+introduction)\\s*[:：]?|$)"
    );

    public ParsedPdf parse(byte[] bytes, String originalFileName) throws IOException {
        try (PDDocument document = Loader.loadPDF(bytes)) {
            if (document.isEncrypted()) {
                throw new IOException("暂不支持加密 PDF");
            }

            int pages = document.getNumberOfPages();
            PDDocumentInformation information = document.getDocumentInformation();
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setStartPage(1);
            stripper.setEndPage(Math.min(pages, 5));
            String text = normalizeText(stripper.getText(document));

            String title = firstNonBlank(
                    normalizeOptional(information.getTitle()),
                    inferTitle(text),
                    fileBaseName(originalFileName)
            );
            List<String> authors = splitAuthors(information.getAuthor());
            List<String> keywords = splitKeywords(information.getKeywords());
            String abstractText = extractAbstract(text);
            String doi = extractDoi(text);
            Integer year = extractYear(information);

            return new ParsedPdf(
                    title,
                    authors,
                    keywords,
                    abstractText,
                    doi,
                    year,
                    pages
            );
        }
    }

    public String extractText(byte[] bytes, int maxPages, int maxCharacters)
            throws IOException {
        if (maxPages < 1 || maxCharacters < 1) return "";
        try (PDDocument document = Loader.loadPDF(bytes)) {
            if (document.isEncrypted()) {
                throw new IOException("暂不支持加密 PDF");
            }
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setStartPage(1);
            stripper.setEndPage(Math.min(document.getNumberOfPages(), maxPages));
            String text = normalizeText(stripper.getText(document));
            if (text.length() <= maxCharacters) return text;
            int endIndex = maxCharacters;
            if (Character.isHighSurrogate(text.charAt(endIndex - 1))
                    && Character.isLowSurrogate(text.charAt(endIndex))) {
                endIndex--;
            }
            return text.substring(0, endIndex);
        }
    }

    private String normalizeText(String text) {
        if (text == null) return "";
        return text
                .replace('\u0000', ' ')
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .replaceAll("[\\t ]+", " ")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
    }

    private String inferTitle(String text) {
        if (text.isBlank()) return null;
        for (String line : text.split("\\n")) {
            String candidate = line.trim();
            if (candidate.length() >= 8
                    && candidate.length() <= 300
                    && !candidate.matches("(?i)^(arxiv|doi|https?://).*")
                    && !candidate.matches("^\\d+$")) {
                return candidate;
            }
        }
        return null;
    }

    private List<String> splitAuthors(String authorText) {
        String normalized = normalizeOptional(authorText);
        if (normalized == null) return List.of();

        String[] values = normalized.split("(?i)\\s*(?:;|，|\\band\\b)\\s*");
        if (values.length == 1 && normalized.contains(",")) {
            values = normalized.split("\\s*,\\s*");
        }
        return distinctValues(values, 200);
    }

    private List<String> splitKeywords(String keywordText) {
        String normalized = normalizeOptional(keywordText);
        if (normalized == null) return List.of();
        return distinctValues(normalized.split("\\s*[,;，；]\\s*"), 100);
    }

    private List<String> distinctValues(String[] values, int maxLength) {
        List<String> result = new ArrayList<>();
        List<String> normalizedValues = new ArrayList<>();
        for (String value : values) {
            String normalized = normalizeOptional(value);
            if (normalized == null || normalized.length() > maxLength) continue;
            String identity = normalized.toLowerCase(Locale.ROOT);
            if (!normalizedValues.contains(identity)) {
                normalizedValues.add(identity);
                result.add(normalized);
            }
        }
        return List.copyOf(result);
    }

    private String extractAbstract(String text) {
        Matcher matcher = ABSTRACT_PATTERN.matcher(text);
        if (!matcher.find()) return "";
        return matcher.group(1)
                .replaceAll("\\s*\\n\\s*", " ")
                .replaceAll("\\s{2,}", " ")
                .trim();
    }

    private String extractDoi(String text) {
        Matcher matcher = DOI_PATTERN.matcher(text);
        if (!matcher.find()) return "";
        return matcher.group()
                .replaceAll("[.,;:)\\]]+$", "");
    }

    private Integer extractYear(PDDocumentInformation information) {
        Calendar creationDate = information.getCreationDate();
        if (creationDate == null) return null;
        int year = creationDate.get(Calendar.YEAR);
        return year >= 1000 && year <= 2100 ? year : null;
    }

    private String fileBaseName(String fileName) {
        String normalized = fileName.replace('\\', '/');
        int slash = normalized.lastIndexOf('/');
        if (slash >= 0) normalized = normalized.substring(slash + 1);
        return normalized.replaceFirst("(?i)\\.pdf$", "");
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) return value;
        }
        return "未命名科研论文";
    }

    private String normalizeOptional(String value) {
        if (value == null) return null;
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
