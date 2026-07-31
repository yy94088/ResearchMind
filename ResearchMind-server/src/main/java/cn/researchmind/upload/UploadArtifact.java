package cn.researchmind.upload;

public record UploadArtifact(
        String uploadId,
        String originalFileName,
        long fileSize,
        String objectKey
) {
}
