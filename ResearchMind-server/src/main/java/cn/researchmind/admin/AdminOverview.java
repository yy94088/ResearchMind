package cn.researchmind.admin;

public record AdminOverview(
        int totalUsers,
        int activeUsers,
        int disabledUsers,
        int totalPapers,
        int totalTeams,
        int pendingUploads,
        int operationsToday
) {
}
