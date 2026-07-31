package cn.researchmind.paper;

import java.io.InputStream;

public record PaperFileDownload(
        InputStream inputStream,
        String fileName,
        long fileSize
) {
}
