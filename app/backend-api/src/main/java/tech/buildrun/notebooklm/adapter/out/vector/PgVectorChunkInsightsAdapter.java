package tech.buildrun.notebooklm.adapter.out.vector;

import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import tech.buildrun.notebooklm.core.domain.chunk.ChunkText;
import tech.buildrun.notebooklm.core.domain.chunk.EnrichedChunk;
import tech.buildrun.notebooklm.core.port.out.vector.ChunkInsightsPort;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
public class PgVectorChunkInsightsAdapter implements ChunkInsightsPort {

    private final NamedParameterJdbcTemplate jdbc;
    private final VectorStore vectorStore;

    public PgVectorChunkInsightsAdapter(NamedParameterJdbcTemplate jdbc, VectorStore vectorStore) {
        this.jdbc = jdbc;
        this.vectorStore = vectorStore;
    }

    @Override
    public List<ChunkText> findWithoutKeywords(List<UUID> sourceIds) {
        return jdbc.query("""
                SELECT id, content FROM vector_store
                WHERE metadata->>'source_id' IN (:sourceIds) AND metadata->>'excerpt_keywords' IS NULL
                """,
                Map.of("sourceIds", asText(sourceIds)),
                (rs, rowNum) -> new ChunkText(rs.getString("id"), rs.getString("content")));
    }

    @Override
    public void updateEnrichment(EnrichedChunk chunk) {
        jdbc.update("""
                UPDATE vector_store
                SET metadata = (metadata::jsonb || jsonb_build_object(
                        'excerpt_keywords', CAST(:keywords AS text), 'section_summary', CAST(:summary AS text)))::json
                WHERE id = :id
                """,
                new MapSqlParameterSource()
                        .addValue("id", UUID.fromString(chunk.id()))
                        .addValue("keywords", chunk.keywords())
                        .addValue("summary", chunk.summary()));
    }

    @Override
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

    @Override
    public List<ChunkText> searchByTopics(List<UUID> sourceIds, List<String> topics, int chunksPerTopic) {
        String filter = filterExpression(sourceIds);
        Map<String, String> chunksById = new LinkedHashMap<>();
        for (String topic : topics) {
            vectorStore.similaritySearch(SearchRequest.builder().query(topic).topK(chunksPerTopic)
                            .filterExpression(filter).build())
                    .forEach(chunk -> chunksById.putIfAbsent(chunk.getId(), chunk.getText()));
        }
        return chunksById.entrySet().stream().map(e -> new ChunkText(e.getKey(), e.getValue())).toList();
    }

    private static String filterExpression(List<UUID> sourceIds) {
        if (sourceIds.isEmpty()) {
            return "source_id == 'none'";
        }
        String ids = sourceIds.stream().map(id -> "'" + id + "'").collect(Collectors.joining(","));
        return "source_id in [" + ids + "]";
    }

    private static List<String> asText(List<UUID> ids) {
        return ids.stream().map(UUID::toString).toList();
    }
}
