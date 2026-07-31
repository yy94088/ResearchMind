package cn.researchmind.note;

import cn.researchmind.security.UserPrincipal;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/papers/{paperId}/note")
public class PaperNoteController {

    private final PaperNoteService noteService;

    public PaperNoteController(PaperNoteService noteService) {
        this.noteService = noteService;
    }

    @GetMapping
    public PaperNote find(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable String paperId
    ) {
        return noteService.find(principal.id(), paperId);
    }

    @PutMapping
    public PaperNote save(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable String paperId,
            @Valid @RequestBody PaperNoteRequest request
    ) {
        return noteService.save(principal.id(), paperId, request);
    }
}
