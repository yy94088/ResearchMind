package cn.researchmind.account;

import java.time.LocalDate;

import cn.researchmind.security.UserPrincipal;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/account")
public class AccountDataExportController {

    private final AccountDataExportService exportService;

    public AccountDataExportController(AccountDataExportService exportService) {
        this.exportService = exportService;
    }

    @GetMapping("/export")
    public ResponseEntity<AccountDataExport> export(
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        String fileName = "ResearchMind-data-backup-" + LocalDate.now() + ".json";
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .cacheControl(org.springframework.http.CacheControl.noStore())
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(fileName).build().toString()
                )
                .body(exportService.export(principal.id()));
    }
}
