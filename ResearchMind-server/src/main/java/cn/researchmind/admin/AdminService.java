package cn.researchmind.admin;

import java.util.List;

import cn.researchmind.common.ApiException;
import cn.researchmind.storage.ObjectStorageService;
import cn.researchmind.upload.UploadRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminService {

    private final AdminRepository adminRepository;
    private final ObjectStorageService objectStorageService;

    public AdminService(
            AdminRepository adminRepository,
            ObjectStorageService objectStorageService
    ) {
        this.adminRepository = adminRepository;
        this.objectStorageService = objectStorageService;
    }

    public AdminOverview overview() {
        return adminRepository.overview();
    }

    public List<AdminUserView> users(String query) {
        return adminRepository.findUsers(query);
    }

    public List<AdminAuditView> audit(int limit) {
        return adminRepository.findAudit(Math.max(1, Math.min(limit, 200)));
    }

    @Transactional
    public AdminUserView updateRole(
            String adminId,
            String userId,
            AdminRoleRequest request
    ) {
        AdminUserView target = requireUser(userId);
        if (adminId.equals(userId) && !"ADMIN".equals(request.role())) {
            throw forbidden("不能移除自己的系统管理员权限");
        }
        ensureLastAdminRemains(target, request.role(), target.status());
        adminRepository.updateRole(userId, request.role());
        adminRepository.addAudit(adminId, "更新用户角色为 " + request.role(), userId);
        return requireUser(userId);
    }

    @Transactional
    public AdminUserView updateStatus(
            String adminId,
            String userId,
            AdminStatusRequest request
    ) {
        AdminUserView target = requireUser(userId);
        if (adminId.equals(userId) && "DISABLED".equals(request.status())) {
            throw forbidden("不能禁用自己的账户");
        }
        ensureLastAdminRemains(target, target.role(), request.status());
        adminRepository.updateStatus(userId, request.status());
        adminRepository.addAudit(adminId, "更新账户状态为 " + request.status(), userId);
        return requireUser(userId);
    }

    @Transactional
    public AdminMaintenanceResult cleanup(String adminId) {
        List<StaleUpload> stale = adminRepository.findStaleUploads();
        int removed = adminRepository.deleteStaleUploads(
                stale.stream().map(StaleUpload::id).toList()
        );
        stale.forEach(upload -> objectStorageService.removeQuietly(
                UploadRepository.objectKey(upload.userId(), upload.id())
        ));
        adminRepository.addAudit(adminId, "清理 24 小时前未完成上传", adminId);
        return new AdminMaintenanceResult(removed);
    }

    private AdminUserView requireUser(String userId) {
        return adminRepository.findUser(userId).orElseThrow(() -> new ApiException(
                HttpStatus.NOT_FOUND,
                "ADMIN_USER_NOT_FOUND",
                "用户不存在"
        ));
    }

    private void ensureLastAdminRemains(
            AdminUserView current,
            String nextRole,
            String nextStatus
    ) {
        if ("ADMIN".equals(current.role())
                && "ACTIVE".equals(current.status())
                && (!"ADMIN".equals(nextRole) || !"ACTIVE".equals(nextStatus))
                && adminRepository.countActiveAdmins() <= 1) {
            throw forbidden("系统必须保留至少一个启用的管理员账户");
        }
    }

    private ApiException forbidden(String message) {
        return new ApiException(HttpStatus.FORBIDDEN, "ADMIN_OPERATION_FORBIDDEN", message);
    }
}
