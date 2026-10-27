package tech.buildrun.notebooklm.repository;

import org.springframework.ai.document.Document;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Acesso direto à tabela vector_store (gerenciada pelo Spring AI) para backfill e agregação de metadata. */
@Repository
public class ChunkMetadataRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public ChunkMetadataRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Document> findWithoutKeywords(List<UUID> sourceIds) {
        return jdbc.query("""
                SELECT id, content FROM vector_store
                WHERE metadata->>'source_id' IN (:sourceIds) AND metadata->>'excerpt_keywords' IS NULL
                """,
                Map.of("sourceIds", asText(sourceIds)),
                (rs, rowNum) -> Document.builder().id(rs.getString("id")).text(rs.getString("content")).build());
    }

    public void updateEnrichment(String chunkId, String keywords, String summary) {
        jdbc.update("""
                UPDATE vector_store
                SET metadata = (metadata::jsonb || jsonb_build_object(
                        'excerpt_keywords', CAST(:keywords AS text), 'section_summary', CAST(:summary AS text)))::json
                WHERE id = :id
                """,
                new MapSqlParameterSource()
                        .addValue("id", UUID.fromString(chunkId))
                        .addValue("keywords", keywords)
                        .addValue("summary", summary));
    }

    public List<String> topKeywords(List<UUID> sourceIds, int limit) {
        return jdbc.queryForList("""
                SELECT lower(trim(kw)) AS keyword
                FROM vector_store v, unnest(string_to_array(v.metadata->>'excerpt_keywords', ',')) AS kw
                WHERE v.metadata->>'source_id' IN (:sourceIds) AND trim(kw) <> ''
                GROUP BY 1
                ORDER BY count(*) DESC, 1
                LIMIT :limit
                """,
                new MapSqlParameterSource().addValue("sourceIds", asText(sourceIds)).addValue("limit", limit),
                String.class);
    }

    private static List<String> asText(List<UUID> ids) {
        return ids.stream().map(UUID::toString).toList();
    }
}
