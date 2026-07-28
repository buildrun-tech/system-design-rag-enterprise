package tech.buildrun.notebooklm.entity;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SourceTest {

    @Test
    void exposesConstructorValuesThroughGetters() {
        var owner = new User("cognito-sub", "user@test.com", "Test User");
        var notebook = new Notebook(owner, "My Notebook", null);
        var source = new Source(notebook, "doc.pdf", SourceType.FILE, "some/key", null);

        assertThat(source.getNotebook()).isEqualTo(notebook);
        assertThat(source.getName()).isEqualTo("doc.pdf");
        assertThat(source.getType()).isEqualTo(SourceType.FILE);
        assertThat(source.getS3Key()).isEqualTo("some/key");
        assertThat(source.getUrl()).isNull();
        assertThat(source.getStatus()).isEqualTo(SourceStatus.PENDING);
        assertThat(source.getErrorMessage()).isNull();
    }

    @Test
    void exposesGeneratedIdAndCreatedAt() {
        var owner = new User("cognito-sub", "user@test.com", "Test User");
        var notebook = new Notebook(owner, "My Notebook", null);
        var source = new Source(notebook, "doc.pdf", SourceType.FILE, "some/key", null);
        var id = UUID.randomUUID();
        var createdAt = Instant.now();

        ReflectionTestUtils.setField(source, "id", id);
        ReflectionTestUtils.setField(source, "createdAt", createdAt);

        assertThat(source.getId()).isEqualTo(id);
        assertThat(source.getCreatedAt()).isEqualTo(createdAt);
    }

    @Test
    void allowsUpdatingStatusAndErrorMessage() {
        var owner = new User("cognito-sub", "user@test.com", "Test User");
        var notebook = new Notebook(owner, "My Notebook", null);
        var source = new Source(notebook, "page", SourceType.URL, null, "https://example.com");

        source.setStatus(SourceStatus.FAILED);
        source.setErrorMessage("boom");

        assertThat(source.getStatus()).isEqualTo(SourceStatus.FAILED);
        assertThat(source.getErrorMessage()).isEqualTo("boom");
        assertThat(source.getUrl()).isEqualTo("https://example.com");
    }
}
