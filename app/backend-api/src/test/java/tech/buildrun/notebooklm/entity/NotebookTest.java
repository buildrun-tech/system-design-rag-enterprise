package tech.buildrun.notebooklm.entity;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class NotebookTest {

    @Test
    void exposesConstructorValuesThroughGetters() {
        var owner = new User("cognito-sub", "user@test.com", "Test User");
        var notebook = new Notebook(owner, "My Notebook", "description");

        assertThat(notebook.getOwner()).isEqualTo(owner);
        assertThat(notebook.getName()).isEqualTo("My Notebook");
        assertThat(notebook.getDescription()).isEqualTo("description");
    }

    @Test
    void exposesGeneratedIdAndTimestamps() {
        var owner = new User("cognito-sub", "user@test.com", "Test User");
        var notebook = new Notebook(owner, "My Notebook", null);
        var id = UUID.randomUUID();
        var createdAt = Instant.now();
        var updatedAt = Instant.now().plusSeconds(1);

        ReflectionTestUtils.setField(notebook, "id", id);
        ReflectionTestUtils.setField(notebook, "createdAt", createdAt);
        ReflectionTestUtils.setField(notebook, "updatedAt", updatedAt);

        assertThat(notebook.getId()).isEqualTo(id);
        assertThat(notebook.getCreatedAt()).isEqualTo(createdAt);
        assertThat(notebook.getUpdatedAt()).isEqualTo(updatedAt);
    }
}
