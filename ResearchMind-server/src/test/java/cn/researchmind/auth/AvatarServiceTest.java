package cn.researchmind.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Optional;

import cn.researchmind.common.ApiException;
import cn.researchmind.storage.ObjectStorageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

@ExtendWith(MockitoExtension.class)
class AvatarServiceTest {

    @Mock private UserAccountRepository userRepository;
    @Mock private ObjectStorageService objectStorageService;
    @InjectMocks private AvatarService avatarService;

    @Test
    void shouldRejectFileWhoseBytesAreNotAnImage() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "avatar.png",
                "image/png",
                "not-a-real-png".getBytes()
        );

        assertThatThrownBy(() -> avatarService.upload("user-1", file))
                .isInstanceOf(ApiException.class)
                .hasMessage("头像必须是有效的 JPG 或 PNG 图片");
    }

    @Test
    void shouldStoreValidPngAndReplaceProfileReference() {
        byte[] pngHeader = {
                (byte) 0x89, 0x50, 0x4e, 0x47,
                0x0d, 0x0a, 0x1a, 0x0a
        };
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "avatar.png",
                "image/png",
                pngHeader
        );
        when(userRepository.findById("user-1"))
                .thenReturn(Optional.of(user(null)))
                .thenReturn(Optional.of(user("avatars/user-1/new.png")));

        UserProfile result = avatarService.upload("user-1", file);

        verify(objectStorageService).put(
                org.mockito.ArgumentMatchers.startsWith("avatars/user-1/"),
                any(),
                eq(8L),
                eq("image/png")
        );
        verify(userRepository).updateAvatar(
                eq("user-1"),
                org.mockito.ArgumentMatchers.endsWith(".png")
        );
        assertThat(result.avatarUrl()).isEqualTo("avatars/user-1/new.png");
    }

    private UserAccount user(String avatarUrl) {
        return new UserAccount(
                "user-1", "researcher", "hash", "user@example.com", "用户",
                avatarUrl, null, null, null, "USER", "ACTIVE",
                LocalDateTime.of(2026, 7, 30, 10, 0)
        );
    }
}
