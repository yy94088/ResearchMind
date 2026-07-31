package cn.researchmind.auth;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class UserAccountRepository {

    private static final String USER_COLUMNS = """
            id, username, password_hash, email, real_name, avatar_url,
            institution, research_direction, bio, role, status, create_time
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final RowMapper<UserAccount> rowMapper = this::mapUser;

    public UserAccountRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<UserAccount> findByAccount(String account) {
        String sql = "SELECT " + USER_COLUMNS
                + " FROM users WHERE username = :account OR email = :account LIMIT 1";
        return jdbcTemplate.query(sql, Map.of("account", account), rowMapper)
                .stream()
                .findFirst();
    }

    public Optional<UserAccount> findById(String id) {
        String sql = "SELECT " + USER_COLUMNS + " FROM users WHERE id = :id LIMIT 1";
        return jdbcTemplate.query(sql, Map.of("id", id), rowMapper)
                .stream()
                .findFirst();
    }

    public Optional<UserAccount> findByEmail(String email) {
        String sql = "SELECT " + USER_COLUMNS + " FROM users WHERE email = :email LIMIT 1";
        return jdbcTemplate.query(sql, Map.of("email", email), rowMapper)
                .stream()
                .findFirst();
    }

    public boolean usernameExists(String username) {
        return count("SELECT COUNT(*) FROM users WHERE username = :value", username) > 0;
    }

    public boolean emailExists(String email) {
        return count("SELECT COUNT(*) FROM users WHERE email = :value", email) > 0;
    }

    public boolean emailExistsExcluding(String email, String userId) {
        Integer result = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM users
                WHERE email = :email AND id <> :userId
                """, Map.of("email", email, "userId", userId), Integer.class);
        return result != null && result > 0;
    }

    public void insert(
            String id,
            String username,
            String passwordHash,
            String email,
            String realName
    ) {
        jdbcTemplate.update("""
                INSERT INTO users (
                    id, username, password_hash, email, real_name, role, status
                ) VALUES (
                    :id, :username, :passwordHash, :email, :realName, 'USER', 'ACTIVE'
                )
                """, Map.of(
                "id", id,
                "username", username,
                "passwordHash", passwordHash,
                "email", email,
                "realName", realName
        ));
    }

    public void updateLastLogin(String id) {
        jdbcTemplate.update(
                "UPDATE users SET last_login_time = CURRENT_TIMESTAMP WHERE id = :id",
                Map.of("id", id)
        );
    }

    public int updateProfile(
            String id,
            String email,
            String realName,
            String institution,
            String researchDirection,
            String bio
    ) {
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("email", email)
                .addValue("realName", realName)
                .addValue("institution", institution)
                .addValue("researchDirection", researchDirection)
                .addValue("bio", bio);
        return jdbcTemplate.update("""
                UPDATE users
                SET email = :email,
                    real_name = :realName,
                    institution = :institution,
                    research_direction = :researchDirection,
                    bio = :bio
                WHERE id = :id
                """, parameters);
    }

    public int updatePassword(String id, String passwordHash) {
        return jdbcTemplate.update("""
                UPDATE users
                SET password_hash = :passwordHash
                WHERE id = :id
                """, Map.of("id", id, "passwordHash", passwordHash));
    }

    public int updatePasswordIfCurrent(
            String id,
            String currentPasswordHash,
            String newPasswordHash
    ) {
        return jdbcTemplate.update("""
                UPDATE users
                SET password_hash = :newPasswordHash
                WHERE id = :id AND password_hash = :currentPasswordHash
                """, Map.of(
                "id", id,
                "currentPasswordHash", currentPasswordHash,
                "newPasswordHash", newPasswordHash
        ));
    }

    public int updateAvatar(String id, String avatarObjectKey) {
        return jdbcTemplate.update("""
                UPDATE users
                SET avatar_url = :avatarObjectKey
                WHERE id = :id
                """, Map.of("id", id, "avatarObjectKey", avatarObjectKey));
    }

    public int clearAvatar(String id) {
        return jdbcTemplate.update(
                "UPDATE users SET avatar_url = NULL WHERE id = :id",
                Map.of("id", id)
        );
    }

    public List<LoginRecord> findRecentLogins(String userId, int limit) {
        return jdbcTemplate.query("""
                SELECT id, ip_address, user_agent, login_time
                FROM login_log
                WHERE user_id = :userId AND login_status = 'SUCCESS'
                ORDER BY login_time DESC
                LIMIT :limit
                """, Map.of("userId", userId, "limit", limit), (resultSet, rowNumber) ->
                new LoginRecord(
                        resultSet.getLong("id"),
                        resultSet.getString("ip_address"),
                        resultSet.getString("user_agent"),
                        resultSet.getObject("login_time", java.time.LocalDateTime.class)
                ));
    }

    public void addLoginLog(
            String userId,
            String account,
            String ipAddress,
            String userAgent,
            boolean success,
            String failureReason
    ) {
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("account", account)
                .addValue("ipAddress", ipAddress)
                .addValue("userAgent", userAgent)
                .addValue("loginStatus", success ? "SUCCESS" : "FAILED")
                .addValue("failureReason", failureReason);
        jdbcTemplate.update("""
                INSERT INTO login_log (
                    user_id, login_account, ip_address, user_agent,
                    login_status, failure_reason
                ) VALUES (
                    :userId, :account, :ipAddress, :userAgent,
                    :loginStatus, :failureReason
                )
                """, parameters);
    }

    private int count(String sql, String value) {
        Integer result = jdbcTemplate.queryForObject(sql, Map.of("value", value), Integer.class);
        return result == null ? 0 : result;
    }

    private UserAccount mapUser(ResultSet resultSet, int rowNumber) throws SQLException {
        return new UserAccount(
                resultSet.getString("id"),
                resultSet.getString("username"),
                resultSet.getString("password_hash"),
                resultSet.getString("email"),
                resultSet.getString("real_name"),
                resultSet.getString("avatar_url"),
                resultSet.getString("institution"),
                resultSet.getString("research_direction"),
                resultSet.getString("bio"),
                resultSet.getString("role"),
                resultSet.getString("status"),
                resultSet.getObject("create_time", java.time.LocalDateTime.class)
        );
    }
}
