package cn.researchmind.paper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import cn.researchmind.upload.UploadArtifact;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class PaperRepository {

    private static final String LIST_SEPARATOR = "|||";
    private static final String AREA_FIELD_SEPARATOR = ":::";

    private static final String PAPER_SELECT = """
            SELECT
                p.id,
                p.title,
                p.title_zh,
                p.abstract_text,
                p.doi,
                p.journal,
                p.publish_year,
                p.file_name,
                p.file_url,
                p.page_count,
                DATE(p.upload_time) AS upload_date,
                COALESCE(author_data.authors, '') AS authors,
                COALESCE(author_data.institutions, '') AS institutions,
                COALESCE(tag_data.tags, '') AS tags,
                COALESCE(area_data.area, '未分类') AS area,
                COALESCE(area_data.areas, '') AS areas,
                CASE WHEN uf.user_id IS NULL THEN 0 ELSE 1 END AS favorite,
                COALESCE(rr.progress, 0) AS progress,
                COALESCE(rr.current_page, 0) AS current_page,
                COALESCE(rr.total_read_seconds, 0) AS total_read_seconds,
                rr.last_read_time
            FROM paper p
            LEFT JOIN (
                SELECT
                    pa.paper_id,
                    GROUP_CONCAT(a.name ORDER BY pa.author_order SEPARATOR '|||') AS authors,
                    GROUP_CONCAT(
                        DISTINCT a.institution ORDER BY a.institution SEPARATOR '|||'
                    ) AS institutions
                FROM paper_author pa
                JOIN author a ON a.id = pa.author_id
                GROUP BY pa.paper_id
            ) author_data ON author_data.paper_id = p.id
            LEFT JOIN (
                SELECT
                    pt.paper_id,
                    GROUP_CONCAT(t.name ORDER BY t.name SEPARATOR '|||') AS tags
                FROM paper_tag pt
                JOIN tag t ON t.id = pt.tag_id
                GROUP BY pt.paper_id
            ) tag_data ON tag_data.paper_id = p.id
            LEFT JOIN (
                SELECT
                    pa.paper_id,
                    SUBSTRING_INDEX(
                        GROUP_CONCAT(
                            ra.name
                            ORDER BY pa.is_primary DESC, pa.confidence DESC, ra.name
                            SEPARATOR '|||'
                        ),
                        '|||',
                        1
                    ) AS area,
                    GROUP_CONCAT(
                        CONCAT(
                            ra.name,
                            ':::',
                            CAST(pa.confidence AS CHAR),
                            ':::',
                            CAST(pa.is_primary AS CHAR)
                        )
                        ORDER BY pa.is_primary DESC, pa.confidence DESC, ra.name
                        SEPARATOR '|||'
                    ) AS areas
                FROM paper_area pa
                JOIN research_area ra ON ra.id = pa.area_id
                GROUP BY pa.paper_id
            ) area_data ON area_data.paper_id = p.id
            LEFT JOIN user_favorite uf
                ON uf.paper_id = p.id AND uf.user_id = :ownerId
            LEFT JOIN reading_record rr
                ON rr.paper_id = p.id AND rr.user_id = :ownerId
            WHERE p.owner_id = :ownerId AND p.deleted = 0
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final RowMapper<PaperView> rowMapper = this::mapPaper;

    public PaperRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<PaperView> findAll(String ownerId) {
        return jdbcTemplate.query(
                PAPER_SELECT + " ORDER BY p.upload_time DESC",
                Map.of("ownerId", ownerId),
                rowMapper
        );
    }

    public Optional<PaperView> findById(String ownerId, String paperId) {
        Map<String, String> parameters = Map.of("ownerId", ownerId, "paperId", paperId);
        return jdbcTemplate.query(
                PAPER_SELECT + " AND p.id = :paperId LIMIT 1",
                parameters,
                rowMapper
        ).stream().findFirst();
    }

    public boolean doiExists(String ownerId, String doi, String excludedPaperId) {
        if (doi == null) return false;

        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("ownerId", ownerId)
                .addValue("doi", doi)
                .addValue("excludedPaperId", excludedPaperId);
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM paper
                WHERE owner_id = :ownerId
                  AND doi = :doi
                  AND deleted = 0
                  AND (:excludedPaperId IS NULL OR id <> :excludedPaperId)
                """, parameters, Integer.class);
        return count != null && count > 0;
    }

    public String insert(
            String ownerId,
            PaperRequest request,
            UploadArtifact uploadedFile
    ) {
        String paperId = UUID.randomUUID().toString();
        MapSqlParameterSource parameters =
                paperParameters(ownerId, paperId, request, uploadedFile);
        jdbcTemplate.update("""
                INSERT INTO paper (
                    id, owner_id, title, title_zh, abstract_text, doi, journal,
                    publish_year, language, file_name, file_url, file_size,
                    page_count, parse_status
                ) VALUES (
                    :id, :ownerId, :title, :titleZh, :abstractText, :doi, :journal,
                    :year, :language, :fileName, :fileUrl, :fileSize,
                    :pages, 'SUCCESS'
                )
                """, parameters);
        replaceRelations(ownerId, paperId, request);
        return paperId;
    }

    public int update(String ownerId, String paperId, PaperRequest request) {
        MapSqlParameterSource parameters =
                paperParameters(ownerId, paperId, request, null);
        int affected = jdbcTemplate.update("""
                UPDATE paper
                SET title = :title,
                    title_zh = :titleZh,
                    abstract_text = :abstractText,
                    doi = :doi,
                    journal = :journal,
                    publish_year = :year,
                    language = :language,
                    file_name = COALESCE(:fileName, file_name),
                    page_count = :pages
                WHERE id = :id AND owner_id = :ownerId AND deleted = 0
                """, parameters);
        if (affected > 0) replaceRelations(ownerId, paperId, request);
        return affected;
    }

    public int updateMissingScalarMetadata(
            String ownerId,
            String paperId,
            PaperMetadataCompletion metadata
    ) {
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("ownerId", ownerId)
                .addValue("paperId", paperId)
                .addValue("titleZh", metadata.titleZh())
                .addValue("abstractText", metadata.abstractText())
                .addValue("doi", metadata.doi())
                .addValue("year", metadata.year())
                .addValue("journal", metadata.journal());
        return jdbcTemplate.update("""
                UPDATE paper
                SET title_zh = CASE
                        WHEN title_zh IS NULL OR TRIM(title_zh) = ''
                        THEN COALESCE(:titleZh, title_zh)
                        ELSE title_zh
                    END,
                    abstract_text = CASE
                        WHEN abstract_text IS NULL OR TRIM(abstract_text) = ''
                        THEN COALESCE(:abstractText, abstract_text)
                        ELSE abstract_text
                    END,
                    doi = CASE
                        WHEN doi IS NULL OR TRIM(doi) = ''
                        THEN COALESCE(:doi, doi)
                        ELSE doi
                    END,
                    publish_year = COALESCE(publish_year, :year),
                    journal = CASE
                        WHEN journal IS NULL OR TRIM(journal) = ''
                        THEN COALESCE(:journal, journal)
                        ELSE journal
                    END
                WHERE id = :paperId AND owner_id = :ownerId AND deleted = 0
                """, parameters);
    }

    public void addAuthors(String paperId, List<String> authorNames) {
        int authorOrder = 1;
        for (String authorName : safeList(authorNames)) {
            String authorId = findOrCreateAuthor(authorName, null);
            jdbcTemplate.update("""
                    INSERT IGNORE INTO paper_author (
                        paper_id, author_id, author_order
                    ) VALUES (
                        :paperId, :authorId, :authorOrder
                    )
                    """, Map.of(
                    "paperId", paperId,
                    "authorId", authorId,
                    "authorOrder", authorOrder++
            ));
        }
    }

    public void addTags(String ownerId, String paperId, List<String> tagNames) {
        for (String tagName : safeList(tagNames)) {
            String tagId = findOrCreateTag(ownerId, tagName);
            jdbcTemplate.update("""
                    INSERT IGNORE INTO paper_tag (paper_id, tag_id)
                    VALUES (:paperId, :tagId)
                    """, Map.of("paperId", paperId, "tagId", tagId));
        }
    }

    public void replaceAreas(String paperId, List<PaperAreaView> areas) {
        Map<String, String> paperParameter = Map.of("paperId", paperId);
        jdbcTemplate.update(
                "DELETE FROM paper_area WHERE paper_id = :paperId",
                paperParameter
        );
        insertAreas(paperId, normalizeAreas(areas));
    }

    public int softDelete(String ownerId, String paperId) {
        return jdbcTemplate.update("""
                UPDATE paper
                SET deleted = 1, doi = NULL, file_url = NULL, file_size = NULL
                WHERE id = :paperId AND owner_id = :ownerId AND deleted = 0
                """, Map.of("ownerId", ownerId, "paperId", paperId));
    }

    public void setFavorite(String ownerId, String paperId, boolean favorite) {
        Map<String, String> parameters = Map.of("ownerId", ownerId, "paperId", paperId);
        if (favorite) {
            jdbcTemplate.update("""
                    INSERT IGNORE INTO user_favorite (user_id, paper_id)
                    VALUES (:ownerId, :paperId)
                    """, parameters);
        } else {
            jdbcTemplate.update("""
                    DELETE FROM user_favorite
                    WHERE user_id = :ownerId AND paper_id = :paperId
                    """, parameters);
        }
    }

    public void setReadingPage(
            String ownerId,
            String paperId,
            int currentPage,
            int progress,
            int readSeconds
    ) {
        jdbcTemplate.update("""
                INSERT INTO reading_record (
                    user_id, paper_id, progress, current_page,
                    total_read_seconds, is_finished, last_read_time
                ) VALUES (
                    :ownerId, :paperId, :progress, :currentPage,
                    :readSeconds, :finished, CURRENT_TIMESTAMP
                )
                ON DUPLICATE KEY UPDATE
                    last_read_time = CASE
                        WHEN current_page < :currentPage
                        THEN CURRENT_TIMESTAMP
                        ELSE last_read_time
                    END,
                    progress = GREATEST(progress, :progress),
                    current_page = GREATEST(current_page, :currentPage),
                    total_read_seconds = LEAST(
                        2147483647,
                        total_read_seconds + :readSeconds
                    ),
                    is_finished = GREATEST(is_finished, :finished)
                """, Map.of(
                "ownerId", ownerId,
                "paperId", paperId,
                "progress", progress,
                "currentPage", currentPage,
                "readSeconds", readSeconds,
                "finished", progress == 100
        ));
    }

    public Optional<StoredPaperFile> findStoredFile(String ownerId, String paperId) {
        return jdbcTemplate.query("""
                SELECT file_url, file_name, file_size
                FROM paper
                WHERE id = :paperId
                  AND owner_id = :ownerId
                  AND deleted = 0
                  AND file_url IS NOT NULL
                LIMIT 1
                """, Map.of("ownerId", ownerId, "paperId", paperId), (resultSet, rowNumber) ->
                new StoredPaperFile(
                        resultSet.getString("file_url"),
                        resultSet.getString("file_name"),
                        resultSet.getLong("file_size")
                )
        ).stream().findFirst();
    }

    private MapSqlParameterSource paperParameters(
            String ownerId,
            String paperId,
            PaperRequest request,
            UploadArtifact uploadedFile
    ) {
        String fileName = uploadedFile == null
                ? normalizeOptional(request.fileName())
                : uploadedFile.originalFileName();
        return new MapSqlParameterSource()
                .addValue("id", paperId)
                .addValue("ownerId", ownerId)
                .addValue("title", normalizeRequired(request.title()))
                .addValue("titleZh", normalizeOptional(request.titleZh()))
                .addValue("abstractText", normalizeOptional(request.abstractText()))
                .addValue("doi", normalizeOptional(request.doi()))
                .addValue("journal", normalizeOptional(request.journal()))
                .addValue("year", request.year())
                .addValue("language", normalizeOptional(request.language()) == null
                        ? "en"
                        : normalizeOptional(request.language()))
                .addValue("fileName", fileName)
                .addValue("fileUrl", uploadedFile == null ? null : uploadedFile.objectKey())
                .addValue("fileSize", uploadedFile == null ? null : uploadedFile.fileSize())
                .addValue("pages", request.pages());
    }

    private void replaceRelations(String ownerId, String paperId, PaperRequest request) {
        Map<String, String> paperParameter = Map.of("paperId", paperId);
        jdbcTemplate.update("DELETE FROM paper_author WHERE paper_id = :paperId", paperParameter);
        jdbcTemplate.update("DELETE FROM paper_tag WHERE paper_id = :paperId", paperParameter);
        jdbcTemplate.update("DELETE FROM paper_area WHERE paper_id = :paperId", paperParameter);

        int authorOrder = 1;
        List<String> authors = safeList(request.authors());
        List<String> institutions = safeList(request.institutions());
        for (int index = 0; index < authors.size(); index++) {
            String authorName = authors.get(index);
            String institution = institutionForAuthor(institutions, authors.size(), index);
            String authorId = findOrCreateAuthor(authorName, institution);
            jdbcTemplate.update("""
                    INSERT INTO paper_author (paper_id, author_id, author_order)
                    VALUES (:paperId, :authorId, :authorOrder)
                    """, Map.of(
                    "paperId", paperId,
                    "authorId", authorId,
                    "authorOrder", authorOrder++
            ));
        }

        for (String tagName : safeList(request.tags())) {
            String tagId = findOrCreateTag(ownerId, tagName);
            jdbcTemplate.update("""
                    INSERT INTO paper_tag (paper_id, tag_id)
                    VALUES (:paperId, :tagId)
                    """, Map.of("paperId", paperId, "tagId", tagId));
        }

        insertAreas(
                paperId,
                normalizeAreas(request.areas(), request.area())
        );
    }

    private void insertAreas(String paperId, List<PaperAreaView> areas) {
        for (PaperAreaView area : areas) {
            String areaId = findOrCreateArea(area.name());
            jdbcTemplate.update("""
                    INSERT INTO paper_area (
                        paper_id, area_id, confidence, is_primary
                    ) VALUES (
                        :paperId, :areaId, :confidence, :primary
                    )
                    """, new MapSqlParameterSource()
                    .addValue("paperId", paperId)
                    .addValue("areaId", areaId)
                    .addValue("confidence", area.confidence())
                    .addValue("primary", area.primary()));
        }
    }

    private String findOrCreateAuthor(String name, String institution) {
        String normalizedName = name.toLowerCase(Locale.ROOT);
        String normalizedInstitution = normalizeOptional(institution);
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("normalizedName", normalizedName)
                .addValue("institution", normalizedInstitution);
        Optional<String> existing = jdbcTemplate.query("""
                SELECT id FROM author
                WHERE normalized_name = :normalizedName
                  AND (institution = :institution
                       OR (institution IS NULL AND :institution IS NULL))
                LIMIT 1
                """, parameters, (rs, rowNum) -> rs.getString("id"))
                .stream().findFirst();
        if (existing.isPresent()) return existing.get();

        String id = UUID.randomUUID().toString();
        jdbcTemplate.update("""
                INSERT INTO author (id, name, normalized_name, institution)
                VALUES (:id, :name, :normalizedName, :institution)
                """, new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("name", name)
                .addValue("normalizedName", normalizedName)
                .addValue("institution", normalizedInstitution));
        return id;
    }

    private String findOrCreateTag(String ownerId, String name) {
        Optional<String> existing = jdbcTemplate.query("""
                SELECT id FROM tag
                WHERE owner_id = :ownerId AND name = :name
                LIMIT 1
                """, Map.of("ownerId", ownerId, "name", name), (rs, rowNum) -> rs.getString("id"))
                .stream().findFirst();
        if (existing.isPresent()) return existing.get();

        String id = UUID.randomUUID().toString();
        jdbcTemplate.update("""
                INSERT INTO tag (id, owner_id, name)
                VALUES (:id, :ownerId, :name)
                """, Map.of("id", id, "ownerId", ownerId, "name", name));
        return id;
    }

    private String findOrCreateArea(String name) {
        Optional<String> existing = jdbcTemplate.query("""
                SELECT id FROM research_area WHERE name = :name LIMIT 1
                """, Map.of("name", name), (rs, rowNum) -> rs.getString("id"))
                .stream().findFirst();
        if (existing.isPresent()) return existing.get();

        String id = UUID.randomUUID().toString();
        jdbcTemplate.update("""
                INSERT INTO research_area (id, name)
                VALUES (:id, :name)
                """, Map.of("id", id, "name", name));
        return id;
    }

    private PaperView mapPaper(ResultSet resultSet, int rowNumber) throws SQLException {
        int progress = resultSet.getInt("progress");
        return new PaperView(
                resultSet.getString("id"),
                resultSet.getString("title"),
                resultSet.getString("title_zh"),
                splitValues(resultSet.getString("authors")),
                splitValues(resultSet.getString("institutions")),
                getNullableInteger(resultSet, "publish_year"),
                resultSet.getString("journal"),
                resultSet.getString("doi"),
                resultSet.getString("area"),
                splitAreas(resultSet.getString("areas")),
                splitValues(resultSet.getString("tags")),
                resultSet.getString("abstract_text"),
                resultSet.getBoolean("favorite"),
                progress >= 100,
                progress,
                resultSet.getInt("current_page"),
                resultSet.getInt("total_read_seconds"),
                getNullableInteger(resultSet, "page_count"),
                resultSet.getString("file_name"),
                resultSet.getString("file_url") != null,
                resultSet.getObject("upload_date", java.time.LocalDate.class),
                resultSet.getObject(
                        "last_read_time",
                        java.time.LocalDateTime.class
                )
        );
    }

    private List<String> splitValues(String value) {
        if (value == null || value.isBlank()) return List.of();
        return Arrays.stream(value.split("\\Q" + LIST_SEPARATOR + "\\E"))
                .map(String::trim)
                .filter(item -> !item.isBlank())
                .toList();
    }

    private List<PaperAreaView> splitAreas(String value) {
        if (value == null || value.isBlank()) {
            return List.of(new PaperAreaView("未分类", 1.0, true));
        }
        List<PaperAreaView> result = Arrays.stream(
                        value.split("\\Q" + LIST_SEPARATOR + "\\E")
                )
                .map(item -> item.split(
                        "\\Q" + AREA_FIELD_SEPARATOR + "\\E",
                        -1
                ))
                .filter(fields -> fields.length == 3 && !fields[0].isBlank())
                .map(fields -> new PaperAreaView(
                        fields[0].trim(),
                        parseConfidence(fields[1]),
                        "1".equals(fields[2])
                                || Boolean.parseBoolean(fields[2])
                ))
                .toList();
        return result.isEmpty()
                ? List.of(new PaperAreaView("未分类", 1.0, true))
                : result;
    }

    private double parseConfidence(String value) {
        try {
            return Math.max(0.0, Math.min(1.0, Double.parseDouble(value)));
        } catch (NumberFormatException exception) {
            return 0.0;
        }
    }

    private List<PaperAreaView> normalizeAreas(
            List<PaperAreaRequest> requestedAreas,
            String legacyArea
    ) {
        List<PaperAreaView> source = requestedAreas == null
                ? List.of()
                : requestedAreas.stream()
                .filter(java.util.Objects::nonNull)
                .map(area -> new PaperAreaView(
                        area.name(),
                        area.confidence() == null ? 0.0 : area.confidence(),
                        area.primary()
                ))
                .toList();
        if (source.isEmpty()) {
            String fallback = normalizeOptional(legacyArea);
            source = List.of(new PaperAreaView(
                    fallback == null ? "未分类" : fallback,
                    1.0,
                    true
            ));
        }
        return normalizeAreas(source);
    }

    private List<PaperAreaView> normalizeAreas(List<PaperAreaView> source) {
        Map<String, PaperAreaView> unique = new LinkedHashMap<>();
        for (PaperAreaView area : source == null ? List.<PaperAreaView>of() : source) {
            if (area == null) continue;
            String name = normalizeOptional(area.name());
            if (name == null) continue;
            String key = name.toLowerCase(Locale.ROOT);
            PaperAreaView existing = unique.get(key);
            double confidence = Math.max(0.0, Math.min(1.0, area.confidence()));
            if (existing == null) {
                unique.put(key, new PaperAreaView(
                        name,
                        confidence,
                        area.primary()
                ));
            } else {
                unique.put(key, new PaperAreaView(
                        existing.name(),
                        Math.max(existing.confidence(), confidence),
                        existing.primary() || area.primary()
                ));
            }
            if (unique.size() == 10) break;
        }
        if (unique.isEmpty()) {
            return List.of(new PaperAreaView("未分类", 1.0, true));
        }
        if (unique.size() > 1) {
            unique.remove("未分类".toLowerCase(Locale.ROOT));
        }

        List<PaperAreaView> values = new java.util.ArrayList<>(unique.values());
        int primaryIndex = -1;
        for (int index = 0; index < values.size(); index++) {
            if (values.get(index).primary()) {
                primaryIndex = index;
                break;
            }
        }
        if (primaryIndex < 0) primaryIndex = 0;
        PaperAreaView primary = values.remove(primaryIndex);
        List<PaperAreaView> result = new java.util.ArrayList<>();
        result.add(new PaperAreaView(
                primary.name(),
                primary.confidence() == 0.0 ? 1.0 : primary.confidence(),
                true
        ));
        for (PaperAreaView area : values) {
            result.add(new PaperAreaView(
                    area.name(),
                    area.confidence() == 0.0 ? 0.7 : area.confidence(),
                    false
            ));
        }
        return List.copyOf(result);
    }

    private Integer getNullableInteger(ResultSet resultSet, String column) throws SQLException {
        int value = resultSet.getInt(column);
        return resultSet.wasNull() ? null : value;
    }

    private List<String> safeList(List<String> values) {
        if (values == null) return List.of();
        Map<String, String> uniqueValues = new LinkedHashMap<>();
        for (String value : values) {
            String normalized = normalizeOptional(value);
            if (normalized != null) {
                uniqueValues.putIfAbsent(normalized.toLowerCase(Locale.ROOT), normalized);
            }
        }
        return List.copyOf(uniqueValues.values());
    }

    private String institutionForAuthor(
            List<String> institutions,
            int authorCount,
            int authorIndex
    ) {
        if (institutions.isEmpty()) return null;
        if (institutions.size() == 1) return institutions.get(0);
        if (institutions.size() == authorCount) return institutions.get(authorIndex);
        if (authorCount >= institutions.size()) {
            return authorIndex < institutions.size() ? institutions.get(authorIndex) : null;
        }
        if (authorIndex < authorCount - 1) return institutions.get(authorIndex);
        return String.join("; ", institutions.subList(authorIndex, institutions.size()));
    }

    private String normalizeRequired(String value) {
        return value.trim();
    }

    private String normalizeOptional(String value) {
        if (value == null) return null;
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
