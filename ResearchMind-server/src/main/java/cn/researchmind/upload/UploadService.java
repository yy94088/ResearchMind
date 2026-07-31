package cn.researchmind.upload;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.UUID;

import cn.researchmind.common.ApiException;
import cn.researchmind.storage.ObjectStorageService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class UploadService {

    private static final long MAX_FILE_SIZE = 50L * 1024L * 1024L;
    private static final byte[] PDF_SIGNATURE = {'%', 'P', 'D', 'F', '-'};

    private final UploadRepository uploadRepository;
    private final ObjectStorageService objectStorageService;
    private final PdfParser pdfParser;
    private final PaperMetadataEnrichmentService metadataEnrichmentService;

    public UploadService(
            UploadRepository uploadRepository,
            ObjectStorageService objectStorageService,
            PdfParser pdfParser,
            PaperMetadataEnrichmentService metadataEnrichmentService
    ) {
        this.uploadRepository = uploadRepository;
        this.objectStorageService = objectStorageService;
        this.pdfParser = pdfParser;
        this.metadataEnrichmentService = metadataEnrichmentService;
    }

    public UploadParseResponse uploadAndParse(
            String userId,
            MultipartFile file,
            boolean aiEnrich
    ) {
        byte[] bytes = validateAndRead(file);
        String fileName = safeFileName(file.getOriginalFilename());
        String uploadId = UUID.randomUUID().toString();
        String objectKey = UploadRepository.objectKey(userId, uploadId);

        uploadRepository.create(uploadId, userId, fileName, bytes.length);
        try {
            uploadRepository.markParsing(uploadId, userId);
            ParsedPdf parsed = pdfParser.parse(bytes, fileName);
            EnrichedPaperMetadata metadata = metadataEnrichmentService.enrich(
                    userId,
                    bytes,
                    fileName,
                    parsed,
                    aiEnrich
            );
            objectStorageService.put(
                    objectKey,
                    new ByteArrayInputStream(bytes),
                    bytes.length,
                    "application/pdf"
            );
            uploadRepository.markSuccess(uploadId, userId);
            return response(
                    uploadId,
                    fileName,
                    bytes.length,
                    parsed.pages(),
                    metadata
            );
        } catch (Exception exception) {
            objectStorageService.removeQuietly(objectKey);
            uploadRepository.markFailed(uploadId, userId, exception.getMessage());
            if (exception instanceof ApiException apiException) throw apiException;
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "PDF_PARSE_FAILED",
                    "PDF 解析失败，请确认文件未损坏且未加密"
            );
        }
    }

    public UploadArtifact requireAvailable(String userId, String uploadId) {
        return uploadRepository.findAvailable(uploadId, userId).orElseThrow(() ->
                new ApiException(
                        HttpStatus.CONFLICT,
                        "UPLOAD_NOT_AVAILABLE",
                        "上传记录不存在、尚未完成或已被使用"
                )
        );
    }

    public void attachToPaper(String userId, String uploadId, String paperId) {
        if (uploadRepository.attachToPaper(uploadId, userId, paperId) == 0) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "UPLOAD_ATTACH_FAILED",
                    "PDF 上传记录无法关联到文献"
            );
        }
    }

    public void discard(String userId, String uploadId) {
        UploadArtifact upload = requireAvailable(userId, uploadId);
        if (uploadRepository.deleteAvailable(uploadId, userId) == 0) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "UPLOAD_DISCARD_FAILED",
                    "上传记录已被使用或无法取消"
            );
        }
        objectStorageService.removeQuietly(upload.objectKey());
    }

    private byte[] validateAndRead(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw badRequest("请选择需要上传的 PDF 文件");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new ApiException(
                    HttpStatus.PAYLOAD_TOO_LARGE,
                    "PDF_TOO_LARGE",
                    "PDF 文件不能超过 50 MB"
            );
        }

        String fileName = safeFileName(file.getOriginalFilename());
        if (!fileName.toLowerCase(java.util.Locale.ROOT).endsWith(".pdf")) {
            throw badRequest("仅支持 PDF 文件");
        }

        try {
            byte[] bytes = file.getBytes();
            if (bytes.length < PDF_SIGNATURE.length
                    || !Arrays.equals(
                            Arrays.copyOf(bytes, PDF_SIGNATURE.length),
                            PDF_SIGNATURE
                    )) {
                throw badRequest("文件内容不是有效的 PDF");
            }
            return bytes;
        } catch (IOException exception) {
            throw badRequest("无法读取上传的 PDF 文件");
        }
    }

    private UploadParseResponse response(
            String uploadId,
            String fileName,
            long fileSize,
            int pages,
            EnrichedPaperMetadata metadata
    ) {
        return new UploadParseResponse(
                uploadId,
                fileName,
                fileSize,
                pages,
                metadata.title(),
                metadata.titleZh(),
                metadata.authors(),
                metadata.keywords(),
                metadata.abstractText(),
                metadata.doi(),
                metadata.year(),
                metadata.journal(),
                metadata.area(),
                metadata.areas(),
                metadata.aiEnriched(),
                metadata.aiEnrichedFields(),
                metadata.aiModel(),
                metadata.aiWarning()
        );
    }

    private String safeFileName(String originalFileName) {
        String value = originalFileName == null ? "document.pdf" : originalFileName;
        value = value.replace('\\', '/');
        int slash = value.lastIndexOf('/');
        if (slash >= 0) value = value.substring(slash + 1);
        value = value.trim();
        if (value.isEmpty()) value = "document.pdf";
        return value.length() <= 500 ? value : value.substring(value.length() - 500);
    }

    private ApiException badRequest(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PDF", message);
    }
}
