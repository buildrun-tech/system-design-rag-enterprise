package tech.buildrun.notebooklm.entity;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SourceChunkTest {

    @Test
    void exposesConstructorValuesThroughGetters() {
        var owner = new User("cognito-sub", "user@test.com", "Test User");
        var notebook = new Notebook(owner, "My Notebook", null);
        var source = new Source(notebook, "doc.pdf", SourceType.FILE, "some/key", null);
        var embedding = new float[] {0.1f, 0.2f};
        var chunk = new SourceChunk(source, "content", embedding, 3, "text-embedding-ada-002");

        assertThat(chunk.getSource()).isEqualTo(source);
        assertThat(chunk.getContent()).isEqualTo("content");
        assertThat(chunk.getEmbedding()).containsExactly(embedding);
        assertThat(chunk.getChunkIndex()).isEqualTo(3);
        assertThat(chunk.getEmbeddingModel()).isEqualTo("text-embedding-ada-002");
    }

    @Test
    void exposesGeneratedIdAndCreatedAt() {
        var owner = new User("cognito-sub", "user@test.com", "Test User");
        var notebook = new Notebook(owner, "My Notebook", null);
        var source = new Source(notebook, "doc.pdf", SourceType.FILE, "some/key", null);
        var chunk = new SourceChunk(source, "content", new float[] {0.1f}, 0, "text-embedding-ada-002");
        var id = UUID.randomUUID();
        var createdAt = Instant.now();

        ReflectionTestUtils.setField(chunk, "id", id);
        ReflectionTestUtils.setField(chunk, "createdAt", createdAt);

        assertThat(chunk.getId()).isEqualTo(id);
        assertThat(chunk.getCreatedAt()).isEqualTo(createdAt);
    }
}
