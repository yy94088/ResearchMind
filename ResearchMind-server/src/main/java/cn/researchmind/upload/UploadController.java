package cn.researchmind.upload;

import cn.researchmind.security.UserPrincipal;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/uploads")
public class UploadController {

    private final UploadService uploadService;

    public UploadController(UploadService uploadService) {
        this.uploadService = uploadService;
    }

    @PostMapping(
            value = "/papers",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public UploadParseResponse uploadPaper(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestPart("file") MultipartFile file,
            @RequestParam(defaultValue = "true") boolean aiEnrich
    ) {
        return uploadService.uploadAndParse(principal.id(), file, aiEnrich);
    }

    @DeleteMapping("/{uploadId}")
    public ResponseEntity<Void> discardUpload(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable String uploadId
    ) {
        uploadService.discard(principal.id(), uploadId);
        return ResponseEntity.noContent().build();
    }
}
