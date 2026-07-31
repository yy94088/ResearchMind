package cn.researchmind.paper;

public record StoredPaperFile(
        String objectKey,
        String fileName,
        long fileSize
) {
}
