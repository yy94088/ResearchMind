package cn.researchmind.note;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class PaperNoteRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final RowMapper<PaperNote> rowMapper = this::mapNote;

    public PaperNoteRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<PaperNote> findDocumentNote(String userId, String paperId) {
        return jdbcTemplate.query("""
                SELECT id, paper_id, note_content, visibility, create_time, update_time
                FROM paper_note
                WHERE user_id = :userId
                  AND paper_id = :paperId
                  AND page_number IS NULL
                  AND selected_text IS NULL
                ORDER BY update_time DESC
                LIMIT 1
                """, Map.of("userId", userId, "paperId", paperId), rowMapper)
                .stream()
                .findFirst();
    }

    public List<PaperNote> findAllByUserId(String userId) {
        return jdbcTemplate.query("""
                SELECT id, paper_id, note_content, visibility, create_time, update_time
                FROM paper_note
                WHERE user_id = :userId
                ORDER BY update_time DESC, id
                """, Map.of("userId", userId), rowMapper);
    }

    public String insertDocumentNote(String userId, String paperId, String content) {
        String id = UUID.randomUUID().toString();
        jdbcTemplate.update("""
                INSERT INTO paper_note (
                    id, user_id, paper_id, note_content, visibility
                ) VALUES (
                    :id, :userId, :paperId, :content, 'PRIVATE'
                )
                """, Map.of(
                "id", id,
                "userId", userId,
                "paperId", paperId,
                "content", content
        ));
        return id;
    }

    public int updateContent(String id, String userId, String content) {
        return jdbcTemplate.update("""
                UPDATE paper_note
                SET note_content = :content
                WHERE id = :id AND user_id = :userId
                """, Map.of("id", id, "userId", userId, "content", content));
    }

    private PaperNote mapNote(ResultSet resultSet, int rowNumber) throws SQLException {
        return new PaperNote(
                resultSet.getString("id"),
                resultSet.getString("paper_id"),
                resultSet.getString("note_content"),
                resultSet.getString("visibility"),
                resultSet.getObject("create_time", LocalDateTime.class),
                resultSet.getObject("update_time", LocalDateTime.class)
        );
    }
}
