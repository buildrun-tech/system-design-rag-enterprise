package tech.buildrun.notebooklm.repository;

import io.awspring.cloud.s3.S3Template;
import io.awspring.cloud.sqs.operations.SqsTemplate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tech.buildrun.notebooklm.AbstractIntegrationTest;
import tech.buildrun.notebooklm.security.TestJwtDecoderConfig;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Valida o SQL contra Postgres real: metadata é JSON (não JSONB) e keywords são uma string separada por vírgula. */
@SpringBootTest
@Import(TestJwtDecoderConfig.class)
class ChunkMetadataRepositoryIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private ChunkMetadataRepository repository;

    @Autowired
    private JdbcTemplate jdbc;

    @MockitoBean
    private S3Template s3Template;

    @MockitoBean
    private SqsTemplate sqsTemplate;

    private UUID insertChunk(UUID sourceId, String metadataExtra) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO vector_store (id, content, metadata) VALUES (?, ?, ?::json)",
                id, "texto " + id, "{\"source_id\":\"" + sourceId + "\"" + metadataExtra + "}");
        return id;
    }

    @Test
    void backfillFlowFindsUpdatesAndAggregatesKeywords() {
        UUID sourceId = UUID.randomUUID();
        UUID otherSource = UUID.randomUUID();
        UUID legacy = insertChunk(sourceId, "");
        insertChunk(sourceId, ",\"excerpt_keywords\":\"Java, spring ,java\"");
        insertChunk(sourceId, ",\"excerpt_keywords\":\"spring, jwt\"");
        insertChunk(otherSource, ",\"excerpt_keywords\":\"ignored\"");

        assertThat(repository.findWithoutKeywords(List.of(sourceId)))
                .extracting(d -> d.getId()).containsExactly(legacy.toString());

        repository.updateEnrichment(legacy.toString(), "spring, jwt", "resumo");

        assertThat(repository.findWithoutKeywords(List.of(sourceId))).isEmpty();
        assertThat(jdbc.queryForObject("SELECT metadata->>'section_summary' FROM vector_store WHERE id = ?",
                String.class, legacy)).isEqualTo("resumo");
        assertThat(jdbc.queryForObject("SELECT metadata->>'source_id' FROM vector_store WHERE id = ?",
                String.class, legacy)).isEqualTo(sourceId.toString());
        assertThat(repository.topKeywords(List.of(sourceId), 2)).containsExactly("spring", "java");
        assertThat(repository.topKeywords(List.of(sourceId), 10)).doesNotContain("ignored");
    }
}
