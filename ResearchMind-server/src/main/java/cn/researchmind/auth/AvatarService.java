package cn.researchmind.auth;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.UUID;

import cn.researchmind.common.ApiException;
import cn.researchmind.storage.ObjectStorageService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class AvatarService {

    private static final long MAX_AVATAR_SIZE = 2L * 1024 * 1024;

    private final UserAccountRepository userRepository;
    private final ObjectStorageService objectStorageService;

    public AvatarService(
            UserAccountRepository userRepository,
            ObjectStorageService objectStorageService
    ) {
        this.userRepository = userRepository;
        this.objectStorageService = objectStorageService;
    }

    @Transactional
    public UserProfile upload(String userId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw badRequest("AVATAR_FILE_REQUIRED", "请选择头像文件");
        }
        if (file.getSize() > MAX_AVATAR_SIZE) {
            throw new ApiException(
                    HttpStatus.PAYLOAD_TOO_LARGE,
                    "AVATAR_TOO_LARGE",
                    "头像文件不能超过 2 MB"
            );
        }

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException exception) {
            throw badRequest("AVATAR_READ_FAILED", "头像文件读取失败");
        }
        AvatarFormat format = detectFormat(bytes);
        UserAccount user = requireUser(userId);
        String objectKey = "avatars/" + userId + "/" + UUID.randomUUID() + format.extension;
        objectStorageService.put(
                objectKey,
                new ByteArrayInputStream(bytes),
                bytes.length,
                format.contentType
        );
        try {
            userRepository.updateAvatar(userId, objectKey);
        } catch (RuntimeException exception) {
            objectStorageService.removeQuietly(objectKey);
            throw exception;
        }
        if (user.avatarUrl() != null && !user.avatarUrl().isBlank()) {
            objectStorageService.removeQuietly(user.avatarUrl());
        }
        return UserProfile.from(requireUser(userId));
    }

    public AvatarDownload download(String userId) {
        UserAccount user = requireUser(userId);
        String objectKey = user.avatarUrl();
        if (objectKey == null || objectKey.isBlank()) {
            throw new ApiException(
                    HttpStatus.NOT_FOUND,
                    "AVATAR_NOT_FOUND",
                    "当前用户尚未上传头像"
            );
        }
        return new AvatarDownload(
                objectStorageService.get(objectKey),
                objectKey.endsWith(".png") ? "image/png" : "image/jpeg"
        );
    }

    @Transactional
    public UserProfile remove(String userId) {
        UserAccount user = requireUser(userId);
        if (user.avatarUrl() != null && !user.avatarUrl().isBlank()) {
            userRepository.clearAvatar(userId);
            objectStorageService.removeQuietly(user.avatarUrl());
        }
        return UserProfile.from(requireUser(userId));
    }

    private AvatarFormat detectFormat(byte[] bytes) {
        if (bytes.length >= 8
                && (bytes[0] & 0xff) == 0x89
                && bytes[1] == 0x50
                && bytes[2] == 0x4e
                && bytes[3] == 0x47
                && bytes[4] == 0x0d
                && bytes[5] == 0x0a
                && bytes[6] == 0x1a
                && bytes[7] == 0x0a) {
            return new AvatarFormat(".png", "image/png");
        }
        if (bytes.length >= 3
                && (bytes[0] & 0xff) == 0xff
                && (bytes[1] & 0xff) == 0xd8
                && (bytes[2] & 0xff) == 0xff) {
            return new AvatarFormat(".jpg", "image/jpeg");
        }
        throw badRequest(
                "AVATAR_FORMAT_INVALID",
                "头像必须是有效的 JPG 或 PNG 图片"
        );
    }

    private UserAccount requireUser(String userId) {
        return userRepository.findById(userId).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "用户不存在"));
    }

    private ApiException badRequest(String code, String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, code, message);
    }

    private record AvatarFormat(String extension, String contentType) {
    }
}
