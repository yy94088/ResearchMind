package cn.researchmind.paper;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;

import cn.researchmind.security.UserPrincipal;
import jakarta.validation.Valid;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/papers")
public class PaperController {

    private final PaperService paperService;

    public PaperController(PaperService paperService) {
        this.paperService = paperService;
    }

    @GetMapping
    public List<PaperView> findAll(@AuthenticationPrincipal UserPrincipal principal) {
        return paperService.findAll(principal.id());
    }

    @GetMapping("/{paperId}")
    public PaperView findById(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable String paperId
    ) {
        return paperService.findById(principal.id(), paperId);
    }

    @PostMapping
    public ResponseEntity<PaperView> create(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody PaperRequest request
    ) {
        PaperView created = paperService.create(principal.id(), request);
        return ResponseEntity
                .created(URI.create("/api/papers/" + created.id()))
                .body(created);
    }

    @PutMapping("/{paperId}")
    public PaperView update(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable String paperId,
            @Valid @RequestBody PaperRequest request
    ) {
        return paperService.update(principal.id(), paperId, request);
    }

    @DeleteMapping("/{paperId}")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable String paperId
    ) {
        paperService.delete(principal.id(), paperId);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{paperId}/favorite")
    public PaperView setFavorite(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable String paperId,
            @RequestBody FavoriteRequest request
    ) {
        return paperService.setFavorite(principal.id(), paperId, request);
    }

    @PutMapping("/{paperId}/progress")
    public PaperView setProgress(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable String paperId,
            @Valid @RequestBody ProgressRequest request
    ) {
        return paperService.setProgress(principal.id(), paperId, request);
    }

    @GetMapping("/{paperId}/file")
    public ResponseEntity<InputStreamResource> openFile(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable String paperId
    ) {
        PaperFileDownload file = paperService.openFile(principal.id(), paperId);
        ContentDisposition disposition = ContentDisposition.inline()
                .filename(file.fileName(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .contentLength(file.fileSize())
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(new InputStreamResource(file.inputStream()));
    }
}
