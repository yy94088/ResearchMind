package cn.researchmind.auth;

import java.io.InputStream;

public record AvatarDownload(
        InputStream inputStream,
        String contentType
) {
}
