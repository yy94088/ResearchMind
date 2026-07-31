package cn.researchmind.graph;

import java.io.IOException;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class KnowledgeGraphRepository {

    private static final TypeReference<Map<String, Object>> PROPERTY_MAP =
            new TypeReference<>() {
            };

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final RowMapper<GraphNodeView> nodeMapper = this::mapNode;
    private final RowMapper<GraphRelationView> relationMapper = this::mapRelation;

    public KnowledgeGraphRepository(
            NamedParameterJdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public void lockOwner(String ownerId) {
        jdbcTemplate.queryForObject("""
                SELECT id
                FROM users
                WHERE id = :ownerId
                FOR UPDATE
                """, Map.of("ownerId", ownerId), String.class);
    }

    public void rebuild(String ownerId) {
        Map<String, String> parameters = Map.of("ownerId", ownerId);
        jdbcTemplate.update(
                "DELETE FROM graph_relation WHERE owner_id = :ownerId",
                parameters
        );
        jdbcTemplate.update(
                "DELETE FROM graph_node WHERE owner_id = :ownerId",
                parameters
        );

        insertPaperNodes(parameters);
        insertAuthorNodes(parameters);
        insertKeywordNodes(parameters);
        insertAreaNodes(parameters);
        insertPaperAuthorRelations(parameters);
        insertPaperKeywordRelations(parameters);
        insertPaperAreaRelations(parameters);
        insertRelatedPaperRelations(parameters);
        insertAuthorCooperationRelations(parameters);
    }

    public List<GraphNodeView> findNodes(String ownerId) {
        return jdbcTemplate.query("""
                SELECT
                    gn.id,
                    gn.reference_id,
                    gn.node_type,
                    gn.name,
                    gn.properties,
                    COALESCE(degrees.degree, 0) AS degree
                FROM graph_node gn
                LEFT JOIN (
                    SELECT node_id, COUNT(*) AS degree
                    FROM (
                        SELECT source_node_id AS node_id
                        FROM graph_relation
                        WHERE owner_id = :ownerId
                        UNION ALL
                        SELECT target_node_id AS node_id
                        FROM graph_relation
                        WHERE owner_id = :ownerId
                    ) relation_ends
                    GROUP BY node_id
                ) degrees ON degrees.node_id = gn.id
                WHERE gn.owner_id = :ownerId
                ORDER BY FIELD(gn.node_type, 'PAPER', 'KEYWORD', 'AUTHOR', 'AREA'),
                         degree DESC,
                         gn.name
                """, Map.of("ownerId", ownerId), nodeMapper);
    }

    public List<GraphRelationView> findRelations(String ownerId) {
        return jdbcTemplate.query("""
                SELECT id, source_node_id, target_node_id, relation_type, weight, properties
                FROM graph_relation
                WHERE owner_id = :ownerId
                ORDER BY relation_type, source_node_id, target_node_id
                """, Map.of("ownerId", ownerId), relationMapper);
    }

    private void insertPaperNodes(Map<String, String> parameters) {
        jdbcTemplate.update("""
                INSERT INTO graph_node (
                    id, owner_id, node_type, reference_id, name, properties
                )
                SELECT
                    UUID(),
                    p.owner_id,
                    'PAPER',
                    p.id,
                    p.title,
                    JSON_OBJECT(
                        'title', p.title,
                        'titleZh', COALESCE(p.title_zh, ''),
                        'year', p.publish_year,
                        'journal', COALESCE(p.journal, ''),
                        'doi', COALESCE(p.doi, '')
                    )
                FROM paper p
                WHERE p.owner_id = :ownerId AND p.deleted = 0
                """, parameters);
    }

    private void insertAuthorNodes(Map<String, String> parameters) {
        jdbcTemplate.update("""
                INSERT INTO graph_node (
                    id, owner_id, node_type, reference_id, name, properties
                )
                SELECT
                    UUID(),
                    p.owner_id,
                    'AUTHOR',
                    a.id,
                    a.name,
                    JSON_OBJECT(
                        'institution', COALESCE(a.institution, ''),
                        'orcid', COALESCE(a.orcid, '')
                    )
                FROM paper p
                JOIN paper_author pa ON pa.paper_id = p.id
                JOIN author a ON a.id = pa.author_id
                WHERE p.owner_id = :ownerId AND p.deleted = 0
                GROUP BY p.owner_id, a.id, a.name, a.institution, a.orcid
                """, parameters);
    }

    private void insertKeywordNodes(Map<String, String> parameters) {
        jdbcTemplate.update("""
                INSERT INTO graph_node (
                    id, owner_id, node_type, reference_id, name, properties
                )
                SELECT
                    UUID(),
                    p.owner_id,
                    'KEYWORD',
                    t.id,
                    t.name,
                    JSON_OBJECT('source', 'USER_OR_PDF')
                FROM paper p
                JOIN paper_tag pt ON pt.paper_id = p.id
                JOIN tag t ON t.id = pt.tag_id
                WHERE p.owner_id = :ownerId AND p.deleted = 0
                GROUP BY p.owner_id, t.id, t.name
                """, parameters);
    }

    private void insertAreaNodes(Map<String, String> parameters) {
        jdbcTemplate.update("""
                INSERT INTO graph_node (
                    id, owner_id, node_type, reference_id, name, properties
                )
                SELECT
                    UUID(),
                    p.owner_id,
                    'AREA',
                    ra.id,
                    ra.name,
                    JSON_OBJECT(
                        'description', COALESCE(ra.description, ''),
                        'color', COALESCE(ra.color, '')
                    )
                FROM paper p
                JOIN paper_area pa ON pa.paper_id = p.id
                JOIN research_area ra ON ra.id = pa.area_id
                WHERE p.owner_id = :ownerId AND p.deleted = 0
                GROUP BY
                    p.owner_id, ra.id, ra.name, ra.description, ra.color
                """, parameters);
    }

    private void insertPaperAuthorRelations(Map<String, String> parameters) {
        jdbcTemplate.update("""
                INSERT INTO graph_relation (
                    id, owner_id, source_node_id, target_node_id,
                    relation_type, weight, properties
                )
                SELECT
                    UUID(),
                    :ownerId,
                    paper_node.id,
                    author_node.id,
                    'AUTHORED_BY',
                    1.00000,
                    JSON_OBJECT('authorOrder', pa.author_order)
                FROM paper p
                JOIN paper_author pa ON pa.paper_id = p.id
                JOIN graph_node paper_node
                    ON paper_node.owner_id = :ownerId
                   AND paper_node.node_type = 'PAPER'
                   AND paper_node.reference_id = p.id
                JOIN graph_node author_node
                    ON author_node.owner_id = :ownerId
                   AND author_node.node_type = 'AUTHOR'
                   AND author_node.reference_id = pa.author_id
                WHERE p.owner_id = :ownerId AND p.deleted = 0
                """, parameters);
    }

    private void insertPaperKeywordRelations(Map<String, String> parameters) {
        jdbcTemplate.update("""
                INSERT INTO graph_relation (
                    id, owner_id, source_node_id, target_node_id,
                    relation_type, weight, properties
                )
                SELECT
                    UUID(),
                    :ownerId,
                    paper_node.id,
                    keyword_node.id,
                    'HAS_KEYWORD',
                    1.00000,
                    JSON_OBJECT()
                FROM paper p
                JOIN paper_tag pt ON pt.paper_id = p.id
                JOIN graph_node paper_node
                    ON paper_node.owner_id = :ownerId
                   AND paper_node.node_type = 'PAPER'
                   AND paper_node.reference_id = p.id
                JOIN graph_node keyword_node
                    ON keyword_node.owner_id = :ownerId
                   AND keyword_node.node_type = 'KEYWORD'
                   AND keyword_node.reference_id = pt.tag_id
                WHERE p.owner_id = :ownerId AND p.deleted = 0
                """, parameters);
    }

    private void insertPaperAreaRelations(Map<String, String> parameters) {
        jdbcTemplate.update("""
                INSERT INTO graph_relation (
                    id, owner_id, source_node_id, target_node_id,
                    relation_type, weight, properties
                )
                SELECT
                    UUID(),
                    :ownerId,
                    paper_node.id,
                    area_node.id,
                    'BELONGS_TO',
                    pa.confidence,
                    JSON_OBJECT('primary', pa.is_primary)
                FROM paper p
                JOIN paper_area pa ON pa.paper_id = p.id
                JOIN graph_node paper_node
                    ON paper_node.owner_id = :ownerId
                   AND paper_node.node_type = 'PAPER'
                   AND paper_node.reference_id = p.id
                JOIN graph_node area_node
                    ON area_node.owner_id = :ownerId
                   AND area_node.node_type = 'AREA'
                   AND area_node.reference_id = pa.area_id
                WHERE p.owner_id = :ownerId AND p.deleted = 0
                """, parameters);
    }

    private void insertRelatedPaperRelations(Map<String, String> parameters) {
        jdbcTemplate.update("""
                INSERT INTO graph_relation (
                    id, owner_id, source_node_id, target_node_id,
                    relation_type, weight, properties
                )
                SELECT
                    UUID(),
                    :ownerId,
                    source_node.id,
                    target_node.id,
                    'RELATED_TO',
                    COUNT(DISTINCT shared.dimension),
                    JSON_OBJECT(
                        'sharedKeywords',
                        COUNT(DISTINCT CASE WHEN shared.dimension LIKE 'KEYWORD:%'
                            THEN shared.dimension END),
                        'sharedAreas',
                        COUNT(DISTINCT CASE WHEN shared.dimension LIKE 'AREA:%'
                            THEN shared.dimension END)
                    )
                FROM (
                    SELECT
                        left_tag.paper_id AS source_paper_id,
                        right_tag.paper_id AS target_paper_id,
                        CONCAT('KEYWORD:', left_tag.tag_id) AS dimension
                    FROM paper_tag left_tag
                    JOIN paper_tag right_tag
                      ON right_tag.tag_id = left_tag.tag_id
                     AND right_tag.paper_id > left_tag.paper_id
                    JOIN paper source_paper
                      ON source_paper.id = left_tag.paper_id
                     AND source_paper.owner_id = :ownerId
                     AND source_paper.deleted = 0
                    JOIN paper target_paper
                      ON target_paper.id = right_tag.paper_id
                     AND target_paper.owner_id = :ownerId
                     AND target_paper.deleted = 0
                    UNION ALL
                    SELECT
                        left_area.paper_id,
                        right_area.paper_id,
                        CONCAT('AREA:', left_area.area_id)
                    FROM paper_area left_area
                    JOIN paper_area right_area
                      ON right_area.area_id = left_area.area_id
                     AND right_area.paper_id > left_area.paper_id
                    JOIN paper source_paper
                      ON source_paper.id = left_area.paper_id
                     AND source_paper.owner_id = :ownerId
                     AND source_paper.deleted = 0
                    JOIN paper target_paper
                      ON target_paper.id = right_area.paper_id
                     AND target_paper.owner_id = :ownerId
                     AND target_paper.deleted = 0
                ) shared
                JOIN graph_node source_node
                    ON source_node.owner_id = :ownerId
                   AND source_node.node_type = 'PAPER'
                   AND source_node.reference_id = shared.source_paper_id
                JOIN graph_node target_node
                    ON target_node.owner_id = :ownerId
                   AND target_node.node_type = 'PAPER'
                   AND target_node.reference_id = shared.target_paper_id
                GROUP BY source_node.id, target_node.id
                """, parameters);
    }

    private void insertAuthorCooperationRelations(Map<String, String> parameters) {
        jdbcTemplate.update("""
                INSERT INTO graph_relation (
                    id, owner_id, source_node_id, target_node_id,
                    relation_type, weight, properties
                )
                SELECT
                    UUID(),
                    :ownerId,
                    source_node.id,
                    target_node.id,
                    'COOPERATES_WITH',
                    COUNT(DISTINCT p.id),
                    JSON_OBJECT('paperCount', COUNT(DISTINCT p.id))
                FROM paper p
                JOIN paper_author source_author ON source_author.paper_id = p.id
                JOIN paper_author target_author
                  ON target_author.paper_id = p.id
                 AND target_author.author_id > source_author.author_id
                JOIN graph_node source_node
                    ON source_node.owner_id = :ownerId
                   AND source_node.node_type = 'AUTHOR'
                   AND source_node.reference_id = source_author.author_id
                JOIN graph_node target_node
                    ON target_node.owner_id = :ownerId
                   AND target_node.node_type = 'AUTHOR'
                   AND target_node.reference_id = target_author.author_id
                WHERE p.owner_id = :ownerId AND p.deleted = 0
                GROUP BY source_node.id, target_node.id
                """, parameters);
    }

    private GraphNodeView mapNode(ResultSet resultSet, int rowNumber) throws SQLException {
        return new GraphNodeView(
                resultSet.getString("id"),
                resultSet.getString("reference_id"),
                GraphNodeType.valueOf(resultSet.getString("node_type")),
                resultSet.getString("name"),
                readProperties(resultSet.getString("properties")),
                resultSet.getInt("degree")
        );
    }

    private GraphRelationView mapRelation(
            ResultSet resultSet,
            int rowNumber
    ) throws SQLException {
        BigDecimal weight = resultSet.getBigDecimal("weight");
        return new GraphRelationView(
                resultSet.getString("id"),
                resultSet.getString("source_node_id"),
                resultSet.getString("target_node_id"),
                GraphRelationType.valueOf(resultSet.getString("relation_type")),
                weight == null ? BigDecimal.ONE : weight,
                readProperties(resultSet.getString("properties"))
        );
    }

    private Map<String, Object> readProperties(String json) throws SQLException {
        if (json == null || json.isBlank()) return Map.of();
        try {
            return objectMapper.readValue(json, PROPERTY_MAP);
        } catch (IOException exception) {
            throw new SQLException("知识图谱节点属性不是有效的 JSON", exception);
        }
    }
}
