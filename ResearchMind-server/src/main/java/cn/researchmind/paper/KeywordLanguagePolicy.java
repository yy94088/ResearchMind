package cn.researchmind.paper;

import java.util.List;

public final class KeywordLanguagePolicy {

    private KeywordLanguagePolicy() {
    }

    public static List<String> filterGeneratedKeywords(
            String originalTitle,
            String abstractText,
            List<String> keywords
    ) {
        if (keywords == null || keywords.isEmpty()) return List.of();
        if (!isEnglishPaper(originalTitle, abstractText)) {
            return List.copyOf(keywords);
        }
        return keywords.stream()
                .filter(keyword -> keyword != null
                        && !keyword.isBlank()
                        && containsLatinLetter(keyword)
                        && !containsHanCharacter(keyword))
                .toList();
    }

    private static boolean isEnglishPaper(
            String originalTitle,
            String abstractText
    ) {
        Language titleLanguage = detect(originalTitle);
        if (titleLanguage != Language.UNKNOWN) {
            return titleLanguage == Language.ENGLISH;
        }
        return detect(abstractText) == Language.ENGLISH;
    }

    private static Language detect(String value) {
        if (value == null || value.isBlank()) return Language.UNKNOWN;
        long hanCount = value.codePoints()
                .filter(KeywordLanguagePolicy::isHan)
                .count();
        long latinCount = value.codePoints()
                .filter(KeywordLanguagePolicy::isLatinLetter)
                .count();
        if (hanCount == 0 && latinCount >= 3) return Language.ENGLISH;
        if (hanCount > 0 && latinCount >= hanCount * 4) {
            return Language.ENGLISH;
        }
        if (hanCount > 0) return Language.CHINESE;
        return Language.UNKNOWN;
    }

    private static boolean containsHanCharacter(String value) {
        return value.codePoints().anyMatch(KeywordLanguagePolicy::isHan);
    }

    private static boolean containsLatinLetter(String value) {
        return value.codePoints().anyMatch(
                KeywordLanguagePolicy::isLatinLetter
        );
    }

    private static boolean isHan(int codePoint) {
        return Character.UnicodeScript.of(codePoint)
                == Character.UnicodeScript.HAN;
    }

    private static boolean isLatinLetter(int codePoint) {
        return Character.isLetter(codePoint)
                && Character.UnicodeScript.of(codePoint)
                == Character.UnicodeScript.LATIN;
    }

    private enum Language {
        CHINESE,
        ENGLISH,
        UNKNOWN
    }
}
