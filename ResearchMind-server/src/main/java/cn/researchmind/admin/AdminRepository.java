package cn.researchmind.admin;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AdminRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public AdminRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public AdminOverview overview() {
        return jdbcTemplate.queryForObject("""
                SELECT
                    (SELECT COUNT(*) FROM users) AS total_users,
                    (SELECT COUNT(*) FROM users WHERE status = 'ACTIVE') AS active_users,
                    (SELECT COUNT(*) FROM users WHERE status = 'DISABLED') AS disabled_users,
                    (SELECT COUNT(*) FROM paper WHERE deleted = 0) AS total_papers,
                    (SELECT COUNT(*) FROM team) AS total_teams,
                    (SELECT COUNT(*) FROM upload_record
                        WHERE paper_id IS NULL AND status IN ('UPLOADING', 'PARSING', 'SUCCESS'))
                        AS pending_uploads,
                    (SELECT COUNT(*) FROM operation_log
                        WHERE operation_time >= CURRENT_DATE) AS operations_today
                """, Map.of(), (resultSet, rowNumber) -> new AdminOverview(
                resultSet.getInt("total_users"),
                resultSet.getInt("active_users"),
                resultSet.getInt("disabled_users"),
                resultSet.getInt("total_papers"),
                resultSet.getInt("total_teams"),
                resultSet.getInt("pending_uploads"),
                resultSet.getInt("operations_today")
        ));
    }

    public List<AdminUserView> findUsers(String query) {
        String normalized = query == null ? "" : query.trim();
        return jdbcTemplate.query("""
                SELECT u.id, u.username, u.email, u.real_name, u.institution,
                       u.role, u.status, u.last_login_time, u.create_time,
                       (SELECT COUNT(*) FROM paper p
                        WHERE p.owner_id = u.id AND p.deleted = 0) AS paper_count,
                       (SELECT COUNT(*) FROM team_member tm
                        WHERE tm.user_id = u.id AND tm.join_status = 'ACCEPTED') AS team_count
                FROM users u
                WHERE :query = ''
                   OR u.username LIKE CONCAT('%', :query, '%')
                   OR u.email LIKE CONCAT('%', :query, '%')
                   OR u.real_name LIKE CONCAT('%', :query, '%')
                ORDER BY u.create_time DESC
                LIMIT 200
                """, Map.of("query", normalized), (resultSet, rowNumber) ->
                new AdminUserView(
                        resultSet.getString("id"),
                        resultSet.getString("username"),
                        resultSet.getString("email"),
                        resultSet.getString("real_name"),
                        resultSet.getString("institution"),
                        resultSet.getString("role"),
                        resultSet.getString("status"),
                        resultSet.getInt("paper_count"),
                        resultSet.getInt("team_count"),
                        resultSet.getObject("last_login_time", java.time.LocalDateTime.class),
                        resultSet.getObject("create_time", java.time.LocalDateTime.class)
                ));
    }

    public Optional<AdminUserView> findUser(String userId) {
        return jdbcTemplate.query("""
                SELECT u.id, u.username, u.email, u.real_name, u.institution,
                       u.role, u.status, u.last_login_time, u.create_time,
                       (SELECT COUNT(*) FROM paper p
                        WHERE p.owner_id = u.id AND p.deleted = 0) AS paper_count,
                       (SELECT COUNT(*) FROM team_member tm
                        WHERE tm.user_id = u.id AND tm.join_status = 'ACCEPTED') AS team_count
                FROM users u
                WHERE u.id = :userId
                LIMIT 1
                """, Map.of("userId", userId), (resultSet, rowNumber) ->
                new AdminUserView(
                        resultSet.getString("id"),
                        resultSet.getString("username"),
                        resultSet.getString("email"),
                        resultSet.getString("real_name"),
                        resultSet.getString("institution"),
                        resultSet.getString("role"),
                        resultSet.getString("status"),
                        resultSet.getInt("paper_count"),
                        resultSet.getInt("team_count"),
                        resultSet.getObject("last_login_time", java.time.LocalDateTime.class),
                        resultSet.getObject("create_time", java.time.LocalDateTime.class)
                )).stream().findFirst();
    }

    public int updateRole(String userId, String role) {
        return jdbcTemplate.update(
                "UPDATE users SET role = :role WHERE id = :userId",
                Map.of("userId", userId, "role", role)
        );
    }

    public int updateStatus(String userId, String status) {
        return jdbcTemplate.update(
                "UPDATE users SET status = :status WHERE id = :userId",
                Map.of("userId", userId, "status", status)
        );
    }

    public int countActiveAdmins() {
        Integer value = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM users
                WHERE role = 'ADMIN' AND status = 'ACTIVE'
                """, Map.of(), Integer.class);
        return value == null ? 0 : value;
    }

    public int countAdmins() {
        Integer value = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM users WHERE role = 'ADMIN'",
                Map.of(),
                Integer.class
        );
        return value == null ? 0 : value;
    }

    public int promoteInitialAdmin(String email) {
        return jdbcTemplate.update("""
                UPDATE users
                SET role = 'ADMIN'
                WHERE email = :email AND status = 'ACTIVE'
                  AND NOT EXISTS (
                      SELECT 1 FROM (SELECT id FROM users WHERE role = 'ADMIN') admins
                  )
                """, Map.of("email", email));
    }

    public List<AdminAuditView> findAudit(int limit) {
        return jdbcTemplate.query("""
                SELECT id, type, actor, module, action, success, ip_address, occurred_at
                FROM (
                    SELECT CONCAT('op-', ol.id) AS id, 'OPERATION' AS type,
                           COALESCE(u.real_name, '已删除用户') AS actor,
                           ol.module AS module, ol.operation AS action,
                           ol.success AS success, ol.ip_address AS ip_address,
                           ol.operation_time AS occurred_at
                    FROM operation_log ol
                    LEFT JOIN users u ON u.id = ol.user_id
                    UNION ALL
                    SELECT CONCAT('login-', ll.id) AS id, 'LOGIN' AS type,
                           COALESCE(u.real_name, ll.login_account) AS actor,
                           'AUTH' AS module,
                           CASE WHEN ll.login_status = 'SUCCESS' THEN '登录成功' ELSE '登录失败' END,
                           ll.login_status = 'SUCCESS' AS success,
                           ll.ip_address AS ip_address, ll.login_time AS occurred_at
                    FROM login_log ll
                    LEFT JOIN users u ON u.id = ll.user_id
                ) audit
                ORDER BY occurred_at DESC
                LIMIT :limit
                """, Map.of("limit", limit), (resultSet, rowNumber) ->
                new AdminAuditView(
                        resultSet.getString("id"),
                        resultSet.getString("type"),
                        resultSet.getString("actor"),
                        resultSet.getString("module"),
                        resultSet.getString("action"),
                        resultSet.getBoolean("success"),
                        resultSet.getString("ip_address"),
                        resultSet.getObject("occurred_at", java.time.LocalDateTime.class)
                ));
    }

    public List<StaleUpload> findStaleUploads() {
        return jdbcTemplate.query("""
                SELECT id, user_id FROM upload_record
                WHERE paper_id IS NULL
                  AND upload_time < DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 24 HOUR)
                """, Map.of(), (resultSet, rowNumber) -> new StaleUpload(
                resultSet.getString("id"),
                resultSet.getString("user_id")
        ));
    }

    public int deleteStaleUploads(List<String> ids) {
        if (ids.isEmpty()) return 0;
        return jdbcTemplate.update("""
                DELETE FROM upload_record
                WHERE id IN (:ids) AND paper_id IS NULL
                """, new MapSqlParameterSource("ids", ids));
    }

    public void addAudit(String adminId, String operation, String targetId) {
        jdbcTemplate.update("""
                INSERT INTO operation_log (
                    user_id, module, operation, target_type, target_id, success
                ) VALUES (
                    :adminId, 'ADMIN', :operation, 'USER', :targetId, 1
                )
                """, Map.of(
                "adminId", adminId,
                "operation", operation,
                "targetId", targetId
        ));
    }
}
