package cn.researchmind.note;

import cn.researchmind.paper.PaperService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaperNoteService {

    private final PaperNoteRepository noteRepository;
    private final PaperService paperService;

    public PaperNoteService(
            PaperNoteRepository noteRepository,
            PaperService paperService
    ) {
        this.noteRepository = noteRepository;
        this.paperService = paperService;
    }

    public PaperNote find(String userId, String paperId) {
        paperService.findById(userId, paperId);
        return noteRepository.findDocumentNote(userId, paperId).orElse(null);
    }

    @Transactional
    public PaperNote save(String userId, String paperId, PaperNoteRequest request) {
        paperService.findById(userId, paperId);
        String content = request.content();
        PaperNote existing = noteRepository.findDocumentNote(userId, paperId).orElse(null);
        if (existing == null) {
            noteRepository.insertDocumentNote(userId, paperId, content);
        } else {
            noteRepository.updateContent(existing.id(), userId, content);
        }
        return noteRepository.findDocumentNote(userId, paperId).orElseThrow();
    }
}
