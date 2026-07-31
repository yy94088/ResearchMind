package cn.researchmind.account;

import java.time.Clock;
import java.time.Instant;

import cn.researchmind.auth.AuthService;
import cn.researchmind.note.PaperNoteRepository;
import cn.researchmind.paper.PaperService;
import cn.researchmind.preference.UserPreferenceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class AccountDataExportService {

    private final AuthService authService;
    private final UserPreferenceService preferenceService;
    private final PaperService paperService;
    private final PaperNoteRepository noteRepository;
    private final Clock clock;

    @Autowired
    public AccountDataExportService(
            AuthService authService,
            UserPreferenceService preferenceService,
            PaperService paperService,
            PaperNoteRepository noteRepository
    ) {
        this(
                authService,
                preferenceService,
                paperService,
                noteRepository,
                Clock.systemUTC()
        );
    }

    AccountDataExportService(
            AuthService authService,
            UserPreferenceService preferenceService,
            PaperService paperService,
            PaperNoteRepository noteRepository,
            Clock clock
    ) {
        this.authService = authService;
        this.preferenceService = preferenceService;
        this.paperService = paperService;
        this.noteRepository = noteRepository;
        this.clock = clock;
    }

    public AccountDataExport export(String userId) {
        return new AccountDataExport(
                "researchmind-account-backup",
                1,
                Instant.now(clock),
                authService.getCurrentUser(userId),
                preferenceService.find(userId),
                paperService.findAll(userId),
                noteRepository.findAllByUserId(userId),
                authService.getRecentLogins(userId)
        );
    }
}
