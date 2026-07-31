package cn.researchmind.system;

import java.time.OffsetDateTime;

import cn.researchmind.storage.ObjectStorageService;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class SystemStatusService {

    private final JdbcTemplate jdbcTemplate;
    private final RedisConnectionFactory redisConnectionFactory;
    private final ObjectStorageService objectStorageService;

    public SystemStatusService(
            JdbcTemplate jdbcTemplate,
            RedisConnectionFactory redisConnectionFactory,
            ObjectStorageService objectStorageService
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.redisConnectionFactory = redisConnectionFactory;
        this.objectStorageService = objectStorageService;
    }

    public SystemStatus getStatus() {
        Integer tableCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE()",
                Integer.class
        );

        String redisStatus;
        try (RedisConnection connection = redisConnectionFactory.getConnection()) {
            redisStatus = connection.ping();
        }

        String objectStorageStatus;
        try {
            objectStorageService.verify();
            objectStorageStatus = "UP";
        } catch (RuntimeException exception) {
            objectStorageStatus = "DOWN";
        }

        return new SystemStatus(
                "UP",
                "UP",
                tableCount == null ? 0 : tableCount,
                redisStatus == null ? "UNKNOWN" : redisStatus,
                objectStorageStatus,
                OffsetDateTime.now()
        );
    }
}
