package cn.researchmind.common;

import java.time.OffsetDateTime;
import java.util.Map;

public record ApiError(
        int status,
        String code,
        String message,
        String path,
        OffsetDateTime timestamp,
        Map<String, String> fieldErrors
) {
}
