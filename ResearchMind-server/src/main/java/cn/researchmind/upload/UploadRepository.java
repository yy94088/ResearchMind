package cn.researchmind.upload;

import java.util.Map;
import java.util.Optional;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class UploadRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public UploadRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void create(
            String uploadId,
            String userId,
            String originalFileName,
            long fileSize
    ) {
        jdbcTemplate.update("""
                INSERT INTO upload_record (
                    id, user_id, original_file_name, file_size, batch_no, status
                ) VALUES (
                    :uploadId, :userId, :fileName, :fileSize, :uploadId, 'UPLOADING'
                )
                """, Map.of(
                "uploadId", uploadId,
                "userId", userId,
                "fileName", originalFileName,
                "fileSize", fileSize
        ));
    }

    public void markParsing(String uploadId, String userId) {
        updateStatus(uploadId, userId, "PARSING", null, false);
    }

    public void markSuccess(String uploadId, String userId) {
        updateStatus(uploadId, userId, "SUCCESS", null, true);
    }

    public void markFailed(String uploadId, String userId, String errorMessage) {
        updateStatus(uploadId, userId, "FAILED", truncate(errorMessage, 1000), true);
    }

    public Optional<UploadArtifact> findAvailable(String uploadId, String userId) {
        return jdbcTemplate.query("""
                SELECT id, original_file_name, file_size
                FROM upload_record
                WHERE id = :uploadId
                  AND user_id = :userId
                  AND status = 'SUCCESS'
                  AND paper_id IS NULL
                LIMIT 1
                """, Map.of("uploadId", uploadId, "userId", userId), (resultSet, rowNumber) ->
                new UploadArtifact(
                        resultSet.getString("id"),
                        resultSet.getString("original_file_name"),
                        resultSet.getLong("file_size"),
                        objectKey(userId, resultSet.getString("id"))
                )
        ).stream().findFirst();
    }

    public int attachToPaper(String uploadId, String userId, String paperId) {
        return jdbcTemplate.update("""
                UPDATE upload_record
                SET paper_id = :paperId
                WHERE id = :uploadId
                  AND user_id = :userId
                  AND status = 'SUCCESS'
                  AND paper_id IS NULL
                """, Map.of(
                "uploadId", uploadId,
                "userId", userId,
                "paperId", paperId
        ));
    }

    public int deleteAvailable(String uploadId, String userId) {
        return jdbcTemplate.update("""
                DELETE FROM upload_record
                WHERE id = :uploadId
                  AND user_id = :userId
                  AND status = 'SUCCESS'
                  AND paper_id IS NULL
                """, Map.of("uploadId", uploadId, "userId", userId));
    }

    public static String objectKey(String userId, String uploadId) {
        return userId + "/" + uploadId + ".pdf";
    }

    private void updateStatus(
            String uploadId,
            String userId,
            String status,
            String errorMessage,
            boolean finished
    ) {
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("uploadId", uploadId)
                .addValue("userId", userId)
                .addValue("status", status)
                .addValue("errorMessage", errorMessage)
                .addValue("finished", finished);
        jdbcTemplate.update("""
                UPDATE upload_record
                SET status = :status,
                    error_message = :errorMessage,
                    finish_time = CASE WHEN :finished THEN CURRENT_TIMESTAMP ELSE NULL END
                WHERE id = :uploadId AND user_id = :userId
                """, parameters);
    }

    private String truncate(String value, int maxLength) {
        if (value == null) return "未知错误";
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
