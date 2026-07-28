package tech.buildrun.notebooklm.repository;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import tech.buildrun.notebooklm.AbstractIntegrationTest;
import tech.buildrun.notebooklm.entity.Notebook;
import tech.buildrun.notebooklm.entity.Source;
import tech.buildrun.notebooklm.entity.SourceChunk;
import tech.buildrun.notebooklm.entity.SourceType;
import tech.buildrun.notebooklm.entity.User;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
class SourceChunkRepositoryTest extends AbstractIntegrationTest {

    @Autowired
    private SourceChunkRepository sourceChunkRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void roundTripsEmbeddingVector() {
        var user = new User(UUID.randomUUID().toString(), "user@test.com", "Test User");
        entityManager.persist(user);

        var notebook = new Notebook(user, "Notebook", null);
        entityManager.persist(notebook);

        var source = new Source(notebook, "doc.pdf", SourceType.FILE, "some/key", null);
        entityManager.persist(source);

        var embedding = new float[1536];
        for (int i = 0; i < embedding.length; i++) {
            embedding[i] = i * 0.001f;
        }

        var chunk = new SourceChunk(source, "chunk content", embedding, 0, "text-embedding-ada-002");
        var saved = sourceChunkRepository.saveAndFlush(chunk);
        entityManager.clear();

        var reloaded = sourceChunkRepository.findById(saved.getId()).orElseThrow();

        assertThat(reloaded.getEmbedding()).containsExactly(embedding);
    }
}
