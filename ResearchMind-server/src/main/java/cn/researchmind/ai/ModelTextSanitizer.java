package cn.researchmind.ai;

import java.text.Normalizer;

import org.springframework.stereotype.Component;

@Component
public class ModelTextSanitizer {

    public String sanitize(String value) {
        if (value == null || value.isEmpty()) return "";

        StringBuilder result = new StringBuilder(value.length());
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            int codePoint;
            if (Character.isHighSurrogate(current)) {
                if (index + 1 >= value.length()
                        || !Character.isLowSurrogate(value.charAt(index + 1))) {
                    continue;
                }
                codePoint = Character.toCodePoint(current, value.charAt(++index));
            } else if (Character.isLowSurrogate(current)) {
                continue;
            } else {
                codePoint = current;
            }

            if (codePoint == '\n' || codePoint == '\t') {
                result.appendCodePoint(codePoint);
                continue;
            }
            if (isUnsafe(codePoint)) continue;
            result.appendCodePoint(codePoint);
        }
        return Normalizer.normalize(result, Normalizer.Form.NFC).trim();
    }

    private boolean isUnsafe(int codePoint) {
        int type = Character.getType(codePoint);
        return type == Character.CONTROL
                || type == Character.FORMAT
                || type == Character.PRIVATE_USE
                || type == Character.SURROGATE
                || type == Character.UNASSIGNED
                || codePoint == 0xFFFD
                || (codePoint >= 0xFDD0 && codePoint <= 0xFDEF)
                || (codePoint & 0xFFFE) == 0xFFFE;
    }
}
