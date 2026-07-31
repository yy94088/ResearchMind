package cn.researchmind.team;

import java.net.URI;
import java.util.List;

import cn.researchmind.security.UserPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/teams")
public class TeamController {

    private final TeamService teamService;

    public TeamController(TeamService teamService) {
        this.teamService = teamService;
    }

    @GetMapping("/current")
    public TeamWorkspaceView current(
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return teamService.findCurrent(principal.id());
    }

    @GetMapping("/invitations")
    public List<TeamInvitationView> invitations(
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return teamService.findInvitations(principal.id());
    }

    @PostMapping
    public ResponseEntity<TeamWorkspaceView> create(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody TeamRequest request
    ) {
        TeamWorkspaceView created = teamService.create(principal.id(), request);
        return ResponseEntity
                .created(URI.create("/api/teams/" + created.id()))
                .body(created);
    }

    @PutMapping("/{teamId}")
    public TeamWorkspaceView update(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable String teamId,
            @Valid @RequestBody TeamRequest request
    ) {
        return teamService.update(principal.id(), teamId, request);
    }

    @PostMapping("/{teamId}/members")
    public TeamWorkspaceView invite(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable String teamId,
            @Valid @RequestBody TeamInviteRequest request
    ) {
        return teamService.invite(principal.id(), teamId, request);
    }

    @PutMapping("/{teamId}/invitation")
    public ResponseEntity<Void> decideInvitation(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable String teamId,
            @RequestBody InvitationDecisionRequest request
    ) {
        teamService.decideInvitation(principal.id(), teamId, request);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{teamId}/members/{memberId}/role")
    public TeamWorkspaceView updateMemberRole(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable String teamId,
            @PathVariable String memberId,
            @Valid @RequestBody TeamRoleRequest request
    ) {
        return teamService.updateMemberRole(
                principal.id(),
                teamId,
                memberId,
                request
        );
    }

    @DeleteMapping("/{teamId}/members/{memberId}")
    public TeamWorkspaceView removeMember(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable String teamId,
            @PathVariable String memberId
    ) {
        return teamService.removeMember(principal.id(), teamId, memberId);
    }

    @PostMapping("/{teamId}/collections")
    public TeamWorkspaceView createCollection(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable String teamId,
            @Valid @RequestBody CollectionRequest request
    ) {
        return teamService.createCollection(principal.id(), teamId, request);
    }

    @PutMapping("/{teamId}/collections/{collectionId}/papers")
    public TeamWorkspaceView replaceCollectionPapers(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable String teamId,
            @PathVariable String collectionId,
            @Valid @RequestBody CollectionPapersRequest request
    ) {
        return teamService.replaceCollectionPapers(
                principal.id(),
                teamId,
                collectionId,
                request
        );
    }
}
