package cn.researchmind.ai;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AiAnalysisRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public AiAnalysisRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<StoredAiAnalysis> findLatestSuccessful(
            String userId,
            String paperId,
            String analysisType
    ) {
        return jdbcTemplate.query("""
                SELECT id, result_content
                FROM ai_analysis
                WHERE user_id = :userId
                  AND paper_id = :paperId
                  AND analysis_type = :analysisType
                  AND status = 'SUCCESS'
                ORDER BY finish_time DESC, create_time DESC
                LIMIT 1
                """, Map.of(
                "userId", userId,
                "paperId", paperId,
                "analysisType", analysisType
        ), (resultSet, rowNumber) -> new StoredAiAnalysis(
                resultSet.getString("id"),
                resultSet.getString("result_content")
        )).stream().findFirst();
    }

    public Optional<StoredAiAnalysis> findCached(
            String userId,
            String paperId,
            String analysisType,
            String inputHash
    ) {
        return jdbcTemplate.query("""
                SELECT id, result_content
                FROM ai_analysis
                WHERE user_id = :userId
                  AND paper_id = :paperId
                  AND analysis_type = :analysisType
                  AND input_hash = :inputHash
                  AND status = 'SUCCESS'
                ORDER BY finish_time DESC, create_time DESC
                LIMIT 1
                """, Map.of(
                "userId", userId,
                "paperId", paperId,
                "analysisType", analysisType,
                "inputHash", inputHash
        ), (resultSet, rowNumber) -> new StoredAiAnalysis(
                resultSet.getString("id"),
                resultSet.getString("result_content")
        )).stream().findFirst();
    }

    public String createRunning(
            String userId,
            String paperId,
            String analysisType,
            String model,
            String inputHash
    ) {
        String id = UUID.randomUUID().toString();
        jdbcTemplate.update("""
                INSERT INTO ai_analysis (
                    id, user_id, paper_id, analysis_type, model_name,
                    input_hash, status
                ) VALUES (
                    :id, :userId, :paperId, :analysisType, :model,
                    :inputHash, 'RUNNING'
                )
                """, Map.of(
                "id", id,
                "userId", userId,
                "paperId", paperId,
                "analysisType", analysisType,
                "model", model,
                "inputHash", inputHash
        ));
        return id;
    }

    public void markSuccessful(
            String id,
            String model,
            String resultContent,
            int tokenUsage
    ) {
        jdbcTemplate.update("""
                UPDATE ai_analysis
                SET model_name = :model,
                    result_content = :resultContent,
                    token_usage = :tokenUsage,
                    status = 'SUCCESS',
                    error_message = NULL,
                    finish_time = CURRENT_TIMESTAMP
                WHERE id = :id
                """, new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("model", model)
                .addValue("resultContent", resultContent)
                .addValue("tokenUsage", tokenUsage));
    }

    public void markFailed(String id, String errorMessage) {
        String safeMessage = errorMessage == null ? "AI 处理失败" : errorMessage;
        if (safeMessage.length() > 1000) safeMessage = safeMessage.substring(0, 1000);
        jdbcTemplate.update("""
                UPDATE ai_analysis
                SET status = 'FAILED',
                    error_message = :errorMessage,
                    finish_time = CURRENT_TIMESTAMP
                WHERE id = :id
                """, Map.of("id", id, "errorMessage", safeMessage));
    }
}
