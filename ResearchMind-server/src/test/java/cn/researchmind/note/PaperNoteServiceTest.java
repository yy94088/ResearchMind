package cn.researchmind.note;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Optional;

import cn.researchmind.paper.PaperService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PaperNoteServiceTest {

    @Mock private PaperNoteRepository noteRepository;
    @Mock private PaperService paperService;
    @InjectMocks private PaperNoteService noteService;

    @Test
    void shouldCreateDocumentNoteAfterPaperOwnershipCheck() {
        PaperNote saved = note("note-1", "paper-1", "研究启发");
        when(noteRepository.findDocumentNote("user-1", "paper-1"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(saved));

        PaperNote result = noteService.save(
                "user-1",
                "paper-1",
                new PaperNoteRequest("研究启发")
        );

        verify(paperService).findById("user-1", "paper-1");
        verify(noteRepository).insertDocumentNote("user-1", "paper-1", "研究启发");
        assertThat(result.content()).isEqualTo("研究启发");
    }

    @Test
    void shouldUpdateExistingDocumentNote() {
        PaperNote existing = note("note-1", "paper-1", "旧内容");
        PaperNote saved = note("note-1", "paper-1", "新内容");
        when(noteRepository.findDocumentNote("user-1", "paper-1"))
                .thenReturn(Optional.of(existing))
                .thenReturn(Optional.of(saved));

        PaperNote result = noteService.save(
                "user-1",
                "paper-1",
                new PaperNoteRequest("新内容")
        );

        verify(noteRepository).updateContent("note-1", "user-1", "新内容");
        assertThat(result.content()).isEqualTo("新内容");
    }

    private PaperNote note(String id, String paperId, String content) {
        LocalDateTime time = LocalDateTime.of(2026, 7, 30, 10, 0);
        return new PaperNote(id, paperId, content, "PRIVATE", time, time);
    }
}
