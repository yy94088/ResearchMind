package cn.researchmind.admin;

import java.util.List;

import cn.researchmind.security.UserPrincipal;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    @GetMapping("/overview")
    public AdminOverview overview() {
        return adminService.overview();
    }

    @GetMapping("/users")
    public List<AdminUserView> users(
            @RequestParam(defaultValue = "") String query
    ) {
        return adminService.users(query);
    }

    @PutMapping("/users/{userId}/role")
    public AdminUserView updateRole(
            @AuthenticationPrincipal UserPrincipal principal,
            @org.springframework.web.bind.annotation.PathVariable String userId,
            @Valid @RequestBody AdminRoleRequest request
    ) {
        return adminService.updateRole(principal.id(), userId, request);
    }

    @PutMapping("/users/{userId}/status")
    public AdminUserView updateStatus(
            @AuthenticationPrincipal UserPrincipal principal,
            @org.springframework.web.bind.annotation.PathVariable String userId,
            @Valid @RequestBody AdminStatusRequest request
    ) {
        return adminService.updateStatus(principal.id(), userId, request);
    }

    @GetMapping("/audit")
    public List<AdminAuditView> audit(
            @RequestParam(defaultValue = "100") int limit
    ) {
        return adminService.audit(limit);
    }

    @PostMapping("/maintenance/cleanup")
    public AdminMaintenanceResult cleanup(
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return adminService.cleanup(principal.id());
    }
}
