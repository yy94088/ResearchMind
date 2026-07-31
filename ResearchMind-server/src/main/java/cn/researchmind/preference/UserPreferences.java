package cn.researchmind.preference;

public record UserPreferences(
        boolean resumeReading,
        boolean autoSaveReadingProgress,
        boolean confirmPaperDeletion,
        boolean defaultGridView
) {
    public static UserPreferences defaults() {
        return new UserPreferences(true, true, true, false);
    }
}
