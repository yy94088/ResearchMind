package cn.researchmind.account;

import java.time.Instant;
import java.util.List;

import cn.researchmind.auth.LoginRecord;
import cn.researchmind.auth.UserProfile;
import cn.researchmind.note.PaperNote;
import cn.researchmind.paper.PaperView;
import cn.researchmind.preference.UserPreferences;

public record AccountDataExport(
        String format,
        int version,
        Instant exportedAt,
        UserProfile profile,
        UserPreferences preferences,
        List<PaperView> papers,
        List<PaperNote> notes,
        List<LoginRecord> loginHistory
) {
}
