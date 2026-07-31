package cn.researchmind.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import cn.researchmind.auth.AuthService;
import cn.researchmind.auth.LoginRecord;
import cn.researchmind.auth.UserProfile;
import cn.researchmind.note.PaperNote;
import cn.researchmind.note.PaperNoteRepository;
import cn.researchmind.paper.PaperService;
import cn.researchmind.preference.UserPreferenceService;
import cn.researchmind.preference.UserPreferences;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AccountDataExportServiceTest {

    @Mock private AuthService authService;
    @Mock private UserPreferenceService preferenceService;
    @Mock private PaperService paperService;
    @Mock private PaperNoteRepository noteRepository;

    @Test
    void shouldAssembleServerSideAccountBackup() {
        Instant exportedAt = Instant.parse("2026-07-31T08:00:00Z");
        Clock clock = Clock.fixed(exportedAt, ZoneOffset.UTC);
        UserProfile profile = new UserProfile(
                "user-1",
                "researcher",
                "user@example.com",
                "研究员",
                null,
                null,
                null,
                null,
                "USER",
                LocalDateTime.of(2026, 7, 1, 8, 0)
        );
        UserPreferences preferences = UserPreferences.defaults();
        PaperNote note = new PaperNote(
                "note-1",
                "paper-1",
                "笔记",
                "PRIVATE",
                LocalDateTime.of(2026, 7, 30, 9, 0),
                LocalDateTime.of(2026, 7, 30, 9, 0)
        );
        LoginRecord login = new LoginRecord(
                1,
                "127.0.0.1",
                "Browser",
                LocalDateTime.of(2026, 7, 31, 8, 0)
        );
        when(authService.getCurrentUser("user-1")).thenReturn(profile);
        when(preferenceService.find("user-1")).thenReturn(preferences);
        when(paperService.findAll("user-1")).thenReturn(List.of());
        when(noteRepository.findAllByUserId("user-1")).thenReturn(List.of(note));
        when(authService.getRecentLogins("user-1")).thenReturn(List.of(login));

        AccountDataExport result = new AccountDataExportService(
                authService,
                preferenceService,
                paperService,
                noteRepository,
                clock
        ).export("user-1");

        assertThat(result.exportedAt()).isEqualTo(exportedAt);
        assertThat(result.profile()).isEqualTo(profile);
        assertThat(result.preferences()).isEqualTo(preferences);
        assertThat(result.notes()).containsExactly(note);
        assertThat(result.loginHistory()).containsExactly(login);
    }
}
