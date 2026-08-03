package cn.researchmind.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import cn.researchmind.common.ApiException;
import cn.researchmind.storage.ObjectStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AdminServiceTest {

    @Mock private AdminRepository adminRepository;
    @Mock private ObjectStorageService objectStorageService;

    private AdminService adminService;

    @BeforeEach
    void setUp() {
        adminService = new AdminService(adminRepository, objectStorageService);
    }

    @Test
    void shouldPreventAdminFromDisablingSelf() {
        AdminUserView admin = user("admin-1", "ADMIN", "ACTIVE");
        when(adminRepository.findUser("admin-1")).thenReturn(Optional.of(admin));

        assertThatThrownBy(() -> adminService.updateStatus(
                "admin-1",
                "admin-1",
                new AdminStatusRequest("DISABLED")
        )).isInstanceOf(ApiException.class).hasMessage("不能禁用自己的账户");
    }

    @Test
    void shouldKeepAtLeastOneActiveAdmin() {
        AdminUserView admin = user("admin-2", "ADMIN", "ACTIVE");
        when(adminRepository.findUser("admin-2")).thenReturn(Optional.of(admin));
        when(adminRepository.countActiveAdmins()).thenReturn(1);

        assertThatThrownBy(() -> adminService.updateRole(
                "admin-1",
                "admin-2",
                new AdminRoleRequest("USER")
        )).isInstanceOf(ApiException.class).hasMessage("系统必须保留至少一个启用的管理员账户");
    }

    @Test
    void shouldRemoveStaleUploadRecordsAndObjects() {
        when(adminRepository.findStaleUploads()).thenReturn(List.of(
                new StaleUpload("upload-1", "user-1")
        ));
        when(adminRepository.deleteStaleUploads(List.of("upload-1"))).thenReturn(1);

        AdminMaintenanceResult result = adminService.cleanup("admin-1");

        assertThat(result.removedUploads()).isEqualTo(1);
        verify(objectStorageService).removeQuietly("user-1/upload-1.pdf");
        verify(adminRepository).addAudit(
                "admin-1",
                "清理 24 小时前未完成上传",
                "admin-1"
        );
    }

    private AdminUserView user(String id, String role, String status) {
        return new AdminUserView(
                id,
                id,
                id + "@example.com",
                "管理员",
                null,
                role,
                status,
                0,
                0,
                null,
                LocalDateTime.of(2026, 7, 31, 10, 0)
        );
    }
}
