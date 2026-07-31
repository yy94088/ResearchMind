package cn.researchmind.storage;

import cn.researchmind.config.StorageProperties;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

@Component("objectStorage")
public class ObjectStorageHealthIndicator implements HealthIndicator {

    private final ObjectStorageService objectStorageService;
    private final StorageProperties properties;

    public ObjectStorageHealthIndicator(
            ObjectStorageService objectStorageService,
            StorageProperties properties
    ) {
        this.objectStorageService = objectStorageService;
        this.properties = properties;
    }

    @Override
    public Health health() {
        try {
            objectStorageService.verify();
            return Health.up()
                    .withDetail("provider", "MinIO")
                    .withDetail("bucket", properties.bucket())
                    .build();
        } catch (RuntimeException exception) {
            return Health.down()
                    .withDetail("provider", "MinIO")
                    .withDetail("message", exception.getMessage())
                    .build();
        }
    }
}
