package cn.researchmind.ai;

import cn.researchmind.security.UserPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ai/papers/{paperId}")
public class AiAnalysisController {

    private final AiAnalysisService analysisService;

    public AiAnalysisController(AiAnalysisService analysisService) {
        this.analysisService = analysisService;
    }

    @GetMapping("/analysis")
    public ResponseEntity<AiAnalysisView> findLatest(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable String paperId
    ) {
        return analysisService.findLatest(principal.id(), paperId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PostMapping("/analysis")
    public AiAnalysisView analyze(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable String paperId,
            @RequestParam(defaultValue = "false") boolean refresh
    ) {
        return analysisService.analyze(principal.id(), paperId, refresh);
    }

    @PostMapping("/questions")
    public AiQuestionView answer(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable String paperId,
            @Valid @RequestBody AiQuestionRequest request
    ) {
        return analysisService.answer(principal.id(), paperId, request);
    }
}
