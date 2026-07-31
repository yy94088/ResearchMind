package cn.researchmind.team;

import java.util.List;
import java.util.Locale;

import cn.researchmind.auth.UserAccount;
import cn.researchmind.auth.UserAccountRepository;
import cn.researchmind.common.ApiException;
import cn.researchmind.paper.PaperService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TeamService {

    private final TeamRepository teamRepository;
    private final UserAccountRepository userRepository;
    private final PaperService paperService;

    public TeamService(
            TeamRepository teamRepository,
            UserAccountRepository userRepository,
            PaperService paperService
    ) {
        this.teamRepository = teamRepository;
        this.userRepository = userRepository;
        this.paperService = paperService;
    }

    public TeamWorkspaceView findCurrent(String userId) {
        return teamRepository.findCurrentTeamId(userId)
                .map(teamId -> workspace(teamId, userId))
                .orElse(null);
    }

    public List<TeamInvitationView> findInvitations(String userId) {
        return teamRepository.findInvitations(userId);
    }

    @Transactional
    public TeamWorkspaceView create(String userId, TeamRequest request) {
        if (teamRepository.findCurrentTeamId(userId).isPresent()) {
            throw conflict(
                    "TEAM_ALREADY_JOINED",
                    "你已加入一个团队，请先处理当前团队后再创建"
            );
        }
        String teamId = teamRepository.createTeam(
                userId,
                request.name().trim(),
                normalizeOptional(request.description()),
                normalizeOptional(request.institution())
        );
        teamRepository.addActivity(userId, teamId, "创建了团队");
        return workspace(teamId, userId);
    }

    @Transactional
    public TeamWorkspaceView update(
            String userId,
            String teamId,
            TeamRequest request
    ) {
        TeamInfo team = requireTeam(teamId, userId);
        if (!"OWNER".equals(team.currentUserRole())) {
            throw forbidden("只有团队所有者可以修改团队设置");
        }
        teamRepository.updateTeam(
                teamId,
                request.name().trim(),
                normalizeOptional(request.description()),
                normalizeOptional(request.institution())
        );
        teamRepository.addActivity(userId, teamId, "更新了团队资料");
        return workspace(teamId, userId);
    }

    @Transactional
    public TeamWorkspaceView invite(
            String userId,
            String teamId,
            TeamInviteRequest request
    ) {
        requireManager(teamId, userId);
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        UserAccount invited = userRepository.findByEmail(email).orElseThrow(() ->
                new ApiException(
                        HttpStatus.NOT_FOUND,
                        "INVITED_USER_NOT_FOUND",
                        "该邮箱尚未注册 ResearchMind，暂时无法发送站内邀请"
                ));
        if (invited.id().equals(userId)) {
            throw conflict("CANNOT_INVITE_SELF", "不能邀请自己加入团队");
        }
        String status = teamRepository.findMemberStatus(teamId, invited.id()).orElse(null);
        if ("ACCEPTED".equals(status)) {
            throw conflict("TEAM_MEMBER_ALREADY_EXISTS", "该用户已经是团队成员");
        }
        teamRepository.inviteMember(teamId, invited.id(), request.role());
        teamRepository.addActivity(userId, teamId, "邀请了 " + invited.realName());
        return workspace(teamId, userId);
    }

    @Transactional
    public void decideInvitation(
            String userId,
            String teamId,
            InvitationDecisionRequest request
    ) {
        if (request.accepted()) {
            String currentTeamId = teamRepository.findCurrentTeamId(userId).orElse(null);
            if (currentTeamId != null && !currentTeamId.equals(teamId)) {
                throw conflict(
                        "TEAM_ALREADY_JOINED",
                        "你已加入其他团队，暂时无法接受该邀请"
                );
            }
        }
        if (teamRepository.decideInvitation(teamId, userId, request.accepted()) == 0) {
            throw notFound("待处理的团队邀请不存在");
        }
        teamRepository.addActivity(
                userId,
                teamId,
                request.accepted() ? "接受了团队邀请" : "拒绝了团队邀请"
        );
    }

    @Transactional
    public TeamWorkspaceView updateMemberRole(
            String userId,
            String teamId,
            String memberId,
            TeamRoleRequest request
    ) {
        requireManager(teamId, userId);
        if (teamRepository.updateMemberRole(teamId, memberId, request.role()) == 0) {
            throw notFound("成员不存在、尚未接受邀请或不能修改所有者角色");
        }
        teamRepository.addActivity(userId, teamId, "调整了成员角色");
        return workspace(teamId, userId);
    }

    @Transactional
    public TeamWorkspaceView removeMember(
            String userId,
            String teamId,
            String memberId
    ) {
        requireManager(teamId, userId);
        if (teamRepository.removeMember(teamId, memberId) == 0) {
            throw notFound("成员不存在或不能移出团队所有者");
        }
        teamRepository.addActivity(userId, teamId, "移出了一名团队成员");
        return workspace(teamId, userId);
    }

    @Transactional
    public TeamWorkspaceView createCollection(
            String userId,
            String teamId,
            CollectionRequest request
    ) {
        TeamInfo team = requireTeam(teamId, userId);
        if ("GUEST".equals(team.currentUserRole())) {
            throw forbidden("访客不能创建团队专题");
        }
        teamRepository.createCollection(
                teamId,
                userId,
                request.name().trim(),
                normalizeOptional(request.description()),
                request.color() == null ? "#3156d3" : request.color()
        );
        teamRepository.addActivity(
                userId,
                teamId,
                "创建了专题“" + request.name().trim() + "”"
        );
        return workspace(teamId, userId);
    }

    @Transactional
    public TeamWorkspaceView replaceCollectionPapers(
            String userId,
            String teamId,
            String collectionId,
            CollectionPapersRequest request
    ) {
        TeamInfo team = requireTeam(teamId, userId);
        if ("GUEST".equals(team.currentUserRole())) {
            throw forbidden("访客不能管理团队专题文献");
        }
        if (!teamRepository.collectionBelongsToTeam(collectionId, teamId)) {
            throw notFound("专题不存在或不属于当前团队");
        }
        List<String> paperIds = request.paperIds().stream().distinct().toList();
        for (String paperId : paperIds) {
            paperService.findById(userId, paperId);
        }
        teamRepository.replaceCurrentUserPapers(collectionId, userId, paperIds);
        teamRepository.addActivity(userId, teamId, "更新了共享专题文献");
        return workspace(teamId, userId);
    }

    private TeamWorkspaceView workspace(String teamId, String userId) {
        TeamInfo team = requireTeam(teamId, userId);
        return new TeamWorkspaceView(
                team.id(),
                team.name(),
                team.description(),
                team.institution(),
                team.ownerId(),
                team.currentUserRole(),
                team.createdAt(),
                teamRepository.countSharedPapers(teamId),
                teamRepository.countAnnotations(teamId),
                teamRepository.findMembers(teamId),
                teamRepository.findCollections(teamId, userId),
                teamRepository.findActivities(teamId)
        );
    }

    private TeamInfo requireManager(String teamId, String userId) {
        TeamInfo team = requireTeam(teamId, userId);
        if (!List.of("OWNER", "MANAGER").contains(team.currentUserRole())) {
            throw forbidden("需要团队管理员权限");
        }
        return team;
    }

    private TeamInfo requireTeam(String teamId, String userId) {
        return teamRepository.findTeamInfo(teamId, userId)
                .orElseThrow(() -> notFound("团队不存在或你无权访问"));
    }

    private String normalizeOptional(String value) {
        if (value == null || value.isBlank()) return null;
        return value.trim();
    }

    private ApiException notFound(String message) {
        return new ApiException(HttpStatus.NOT_FOUND, "TEAM_NOT_FOUND", message);
    }

    private ApiException conflict(String code, String message) {
        return new ApiException(HttpStatus.CONFLICT, code, message);
    }

    private ApiException forbidden(String message) {
        return new ApiException(HttpStatus.FORBIDDEN, "TEAM_PERMISSION_DENIED", message);
    }
}
