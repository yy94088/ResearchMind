package cn.researchmind.storage;

import java.io.InputStream;
import java.util.concurrent.atomic.AtomicBoolean;

import cn.researchmind.common.ApiException;
import cn.researchmind.config.StorageProperties;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.GetObjectResponse;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class ObjectStorageService {

    private final MinioClient minioClient;
    private final StorageProperties properties;
    private final AtomicBoolean bucketReady = new AtomicBoolean(false);

    public ObjectStorageService(
            MinioClient minioClient,
            StorageProperties properties
    ) {
        this.minioClient = minioClient;
        this.properties = properties;
    }

    public void put(
            String objectKey,
            InputStream inputStream,
            long size,
            String contentType
    ) {
        try {
            ensureBucket();
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(properties.bucket())
                    .object(objectKey)
                    .stream(inputStream, size, -1L)
                    .contentType(contentType)
                    .build());
        } catch (Exception exception) {
            throw unavailable("文件写入对象存储失败", exception);
        }
    }

    public GetObjectResponse get(String objectKey) {
        try {
            ensureBucket();
            return minioClient.getObject(GetObjectArgs.builder()
                    .bucket(properties.bucket())
                    .object(objectKey)
                    .build());
        } catch (Exception exception) {
            throw unavailable("文件读取失败", exception);
        }
    }

    public void removeQuietly(String objectKey) {
        try {
            ensureBucket();
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(properties.bucket())
                    .object(objectKey)
                    .build());
        } catch (Exception ignored) {
            // 上传失败后的补偿清理不覆盖原始异常。
        }
    }

    public void verify() {
        try {
            boolean exists = minioClient.bucketExists(BucketExistsArgs.builder()
                    .bucket(properties.bucket())
                    .build());
            if (!exists) {
                bucketReady.set(false);
                ensureBucket();
            }
        } catch (Exception exception) {
            throw unavailable("对象存储连接失败", exception);
        }
    }

    private void ensureBucket() throws Exception {
        if (bucketReady.get()) return;
        synchronized (bucketReady) {
            if (bucketReady.get()) return;
            boolean exists = minioClient.bucketExists(BucketExistsArgs.builder()
                    .bucket(properties.bucket())
                    .build());
            if (!exists) {
                minioClient.makeBucket(MakeBucketArgs.builder()
                        .bucket(properties.bucket())
                        .build());
            }
            bucketReady.set(true);
        }
    }

    private ApiException unavailable(String message, Exception cause) {
        ApiException exception = new ApiException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "OBJECT_STORAGE_UNAVAILABLE",
                message
        );
        exception.initCause(cause);
        return exception;
    }
}
