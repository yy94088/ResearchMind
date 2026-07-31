package cn.researchmind.activity;

import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class OperationLogRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public OperationLogRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void insert(
            String userId,
            String module,
            String operation,
            String targetType,
            String targetId,
            String detail
    ) {
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("module", module)
                .addValue("operation", operation)
                .addValue("targetType", targetType)
                .addValue("targetId", targetId)
                .addValue("detail", detail);
        jdbcTemplate.update("""
                INSERT INTO operation_log (
                    user_id, module, operation, target_type, target_id,
                    success, detail
                ) VALUES (
                    :userId, :module, :operation, :targetType, :targetId,
                    1, JSON_OBJECT('text', :detail)
                )
                """, parameters);
    }

    public List<RecentActivityView> findRecent(String userId, int limit) {
        return jdbcTemplate.query("""
                SELECT
                    id,
                    LOWER(module) AS activity_type,
                    operation,
                    COALESCE(
                        JSON_UNQUOTE(JSON_EXTRACT(detail, '$.text')),
                        ''
                    ) AS detail_text,
                    operation_time
                FROM operation_log
                WHERE user_id = :userId AND success = 1
                ORDER BY operation_time DESC
                LIMIT :limit
                """, Map.of("userId", userId, "limit", limit), (resultSet, rowNumber) ->
                new RecentActivityView(
                        resultSet.getLong("id"),
                        resultSet.getString("activity_type"),
                        resultSet.getString("operation"),
                        resultSet.getString("detail_text"),
                        resultSet.getObject("operation_time", java.time.LocalDateTime.class)
                ));
    }
}
