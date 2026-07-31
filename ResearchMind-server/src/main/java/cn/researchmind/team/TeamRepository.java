package cn.researchmind.team;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.Arrays;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class TeamRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public TeamRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<String> findCurrentTeamId(String userId) {
        return jdbcTemplate.query("""
                SELECT t.id
                FROM team t
                JOIN team_member tm ON tm.team_id = t.id
                WHERE tm.user_id = :userId AND tm.join_status = 'ACCEPTED'
                ORDER BY
                    CASE tm.member_role WHEN 'OWNER' THEN 0 ELSE 1 END,
                    t.create_time
                LIMIT 1
                """, Map.of("userId", userId), (resultSet, rowNumber) ->
                resultSet.getString("id")).stream().findFirst();
    }

    public Optional<TeamInfo> findTeamInfo(String teamId, String userId) {
        return jdbcTemplate.query("""
                SELECT
                    t.id, t.name, t.description, t.institution, t.owner_id,
                    tm.member_role, t.create_time
                FROM team t
                JOIN team_member tm
                    ON tm.team_id = t.id
                   AND tm.user_id = :userId
                   AND tm.join_status = 'ACCEPTED'
                WHERE t.id = :teamId
                LIMIT 1
                """, Map.of("teamId", teamId, "userId", userId), (resultSet, rowNumber) ->
                new TeamInfo(
                        resultSet.getString("id"),
                        resultSet.getString("name"),
                        resultSet.getString("description"),
                        resultSet.getString("institution"),
                        resultSet.getString("owner_id"),
                        resultSet.getString("member_role"),
                        resultSet.getObject("create_time", java.time.LocalDateTime.class)
                )).stream().findFirst();
    }

    public String createTeam(
            String ownerId,
            String name,
            String description,
            String institution
    ) {
        String teamId = UUID.randomUUID().toString();
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("id", teamId)
                .addValue("ownerId", ownerId)
                .addValue("name", name)
                .addValue("description", description)
                .addValue("institution", institution);
        jdbcTemplate.update("""
                INSERT INTO team (id, name, description, institution, owner_id)
                VALUES (:id, :name, :description, :institution, :ownerId)
                """, parameters);
        jdbcTemplate.update("""
                INSERT INTO team_member (
                    team_id, user_id, member_role, join_status, join_time
                ) VALUES (
                    :teamId, :ownerId, 'OWNER', 'ACCEPTED', CURRENT_TIMESTAMP
                )
                """, Map.of("teamId", teamId, "ownerId", ownerId));
        return teamId;
    }

    public int updateTeam(
            String teamId,
            String name,
            String description,
            String institution
    ) {
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("teamId", teamId)
                .addValue("name", name)
                .addValue("description", description)
                .addValue("institution", institution);
        return jdbcTemplate.update("""
                UPDATE team
                SET name = :name,
                    description = :description,
                    institution = :institution
                WHERE id = :teamId
                """, parameters);
    }

    public List<TeamMemberView> findMembers(String teamId) {
        return jdbcTemplate.query("""
                SELECT
                    u.id, u.real_name, u.email, u.avatar_url,
                    tm.member_role, tm.join_status, tm.join_time,
                    (
                        SELECT COUNT(*)
                        FROM paper p
                        WHERE p.owner_id = u.id AND p.deleted = 0
                    ) AS paper_count
                FROM team_member tm
                JOIN users u ON u.id = tm.user_id
                WHERE tm.team_id = :teamId
                ORDER BY
                    CASE tm.member_role
                        WHEN 'OWNER' THEN 0
                        WHEN 'MANAGER' THEN 1
                        WHEN 'MEMBER' THEN 2
                        ELSE 3
                    END,
                    tm.create_time
                """, Map.of("teamId", teamId), (resultSet, rowNumber) ->
                new TeamMemberView(
                        resultSet.getString("id"),
                        resultSet.getString("real_name"),
                        resultSet.getString("email"),
                        resultSet.getString("avatar_url"),
                        resultSet.getString("member_role"),
                        resultSet.getString("join_status"),
                        resultSet.getObject("join_time", java.time.LocalDateTime.class),
                        resultSet.getInt("paper_count")
                ));
    }

    public Optional<String> findMemberStatus(String teamId, String userId) {
        return jdbcTemplate.query("""
                SELECT join_status
                FROM team_member
                WHERE team_id = :teamId AND user_id = :userId
                LIMIT 1
                """, Map.of("teamId", teamId, "userId", userId), (resultSet, rowNumber) ->
                resultSet.getString("join_status")).stream().findFirst();
    }

    public void inviteMember(String teamId, String userId, String role) {
        jdbcTemplate.update("""
                INSERT INTO team_member (
                    team_id, user_id, member_role, join_status
                ) VALUES (
                    :teamId, :userId, :role, 'PENDING'
                )
                ON DUPLICATE KEY UPDATE
                    member_role = :role,
                    join_status = 'PENDING',
                    join_time = NULL,
                    create_time = CURRENT_TIMESTAMP
                """, Map.of("teamId", teamId, "userId", userId, "role", role));
    }

    public int decideInvitation(String teamId, String userId, boolean accepted) {
        if (accepted) {
            return jdbcTemplate.update("""
                    UPDATE team_member
                    SET join_status = 'ACCEPTED', join_time = CURRENT_TIMESTAMP
                    WHERE team_id = :teamId
                      AND user_id = :userId
                      AND join_status = 'PENDING'
                    """, Map.of("teamId", teamId, "userId", userId));
        }
        return jdbcTemplate.update("""
                UPDATE team_member
                SET join_status = 'REJECTED', join_time = NULL
                WHERE team_id = :teamId
                  AND user_id = :userId
                  AND join_status = 'PENDING'
                """, Map.of("teamId", teamId, "userId", userId));
    }

    public int updateMemberRole(String teamId, String userId, String role) {
        return jdbcTemplate.update("""
                UPDATE team_member
                SET member_role = :role
                WHERE team_id = :teamId
                  AND user_id = :userId
                  AND member_role <> 'OWNER'
                  AND join_status = 'ACCEPTED'
                """, Map.of("teamId", teamId, "userId", userId, "role", role));
    }

    public int removeMember(String teamId, String userId) {
        return jdbcTemplate.update("""
                DELETE FROM team_member
                WHERE team_id = :teamId
                  AND user_id = :userId
                  AND member_role <> 'OWNER'
                """, Map.of("teamId", teamId, "userId", userId));
    }

    public List<TeamInvitationView> findInvitations(String userId) {
        return jdbcTemplate.query("""
                SELECT
                    t.id, t.name, t.institution, owner.real_name AS inviter_name,
                    tm.member_role, tm.create_time
                FROM team_member tm
                JOIN team t ON t.id = tm.team_id
                JOIN users owner ON owner.id = t.owner_id
                WHERE tm.user_id = :userId AND tm.join_status = 'PENDING'
                ORDER BY tm.create_time DESC
                """, Map.of("userId", userId), (resultSet, rowNumber) ->
                new TeamInvitationView(
                        resultSet.getString("id"),
                        resultSet.getString("name"),
                        resultSet.getString("institution"),
                        resultSet.getString("inviter_name"),
                        resultSet.getString("member_role"),
                        resultSet.getObject("create_time", java.time.LocalDateTime.class)
                ));
    }

    public String createCollection(
            String teamId,
            String ownerId,
            String name,
            String description,
            String color
    ) {
        String id = UUID.randomUUID().toString();
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("teamId", teamId)
                .addValue("ownerId", ownerId)
                .addValue("name", name)
                .addValue("description", description)
                .addValue("color", color);
        jdbcTemplate.update("""
                INSERT INTO collection (
                    id, team_id, owner_id, name, description, cover_color
                ) VALUES (
                    :id, :teamId, :ownerId, :name, :description, :color
                )
                """, parameters);
        return id;
    }

    public List<TeamCollectionView> findCollections(String teamId, String userId) {
        return jdbcTemplate.query("""
                SELECT
                    c.id, c.name, c.description, c.cover_color, c.create_time,
                    COUNT(p.id) AS paper_count,
                    GROUP_CONCAT(
                        CASE WHEN p.owner_id = :userId THEN cp.paper_id END
                        SEPARATOR ','
                    ) AS current_user_papers
                FROM collection c
                LEFT JOIN collection_paper cp ON cp.collection_id = c.id
                LEFT JOIN paper p ON p.id = cp.paper_id AND p.deleted = 0
                WHERE c.team_id = :teamId
                GROUP BY c.id, c.name, c.description, c.cover_color, c.create_time
                ORDER BY c.create_time DESC
                """, Map.of("teamId", teamId, "userId", userId), (resultSet, rowNumber) ->
                new TeamCollectionView(
                        resultSet.getString("id"),
                        resultSet.getString("name"),
                        resultSet.getString("description"),
                        resultSet.getString("cover_color"),
                        resultSet.getInt("paper_count"),
                        resultSet.getObject("create_time", java.time.LocalDateTime.class),
                        splitIds(resultSet.getString("current_user_papers"))
                ));
    }

    public boolean collectionBelongsToTeam(String collectionId, String teamId) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM collection
                WHERE id = :collectionId AND team_id = :teamId
                """, Map.of("collectionId", collectionId, "teamId", teamId), Integer.class);
        return count != null && count > 0;
    }

    public void replaceCurrentUserPapers(
            String collectionId,
            String userId,
            List<String> paperIds
    ) {
        jdbcTemplate.update("""
                DELETE FROM collection_paper
                WHERE collection_id = :collectionId AND added_by = :userId
                """, Map.of("collectionId", collectionId, "userId", userId));
        for (String paperId : paperIds) {
            jdbcTemplate.update("""
                    INSERT INTO collection_paper (
                        collection_id, paper_id, added_by
                    ) VALUES (
                        :collectionId, :paperId, :userId
                    )
                    """, Map.of(
                    "collectionId", collectionId,
                    "paperId", paperId,
                    "userId", userId
            ));
        }
    }

    public int countSharedPapers(String teamId) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(DISTINCT cp.paper_id)
                FROM collection c
                JOIN collection_paper cp ON cp.collection_id = c.id
                WHERE c.team_id = :teamId
                """, Map.of("teamId", teamId), Integer.class);
        return count == null ? 0 : count;
    }

    public int countAnnotations(String teamId) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(DISTINCT pn.id)
                FROM collection c
                JOIN collection_paper cp ON cp.collection_id = c.id
                JOIN paper_note pn
                    ON pn.paper_id = cp.paper_id AND pn.visibility = 'TEAM'
                WHERE c.team_id = :teamId
                """, Map.of("teamId", teamId), Integer.class);
        return count == null ? 0 : count;
    }

    public void addActivity(String userId, String teamId, String operation) {
        jdbcTemplate.update("""
                INSERT INTO operation_log (
                    user_id, module, operation, target_type, target_id, success
                ) VALUES (
                    :userId, 'TEAM', :operation, 'TEAM', :teamId, 1
                )
                """, Map.of("userId", userId, "teamId", teamId, "operation", operation));
    }

    public List<TeamActivityView> findActivities(String teamId) {
        return jdbcTemplate.query("""
                SELECT ol.id, COALESCE(u.real_name, '已删除用户') AS operator_name,
                       ol.operation, ol.operation_time
                FROM operation_log ol
                LEFT JOIN users u ON u.id = ol.user_id
                WHERE ol.module = 'TEAM'
                  AND ol.target_type = 'TEAM'
                  AND ol.target_id = :teamId
                ORDER BY ol.operation_time DESC
                LIMIT 12
                """, Map.of("teamId", teamId), (resultSet, rowNumber) ->
                new TeamActivityView(
                        resultSet.getLong("id"),
                        resultSet.getString("operator_name"),
                        resultSet.getString("operation"),
                        resultSet.getObject("operation_time", java.time.LocalDateTime.class)
                ));
    }

    private List<String> splitIds(String value) {
        if (value == null || value.isBlank()) return List.of();
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(item -> !item.isBlank())
                .toList();
    }
}
