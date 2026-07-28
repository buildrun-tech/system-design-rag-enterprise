package tech.buildrun.notebooklm.entity;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ConversationTest {

    @Test
    void exposesConstructorValuesThroughGetters() {
        var owner = new User("cognito-sub", "user@test.com", "Test User");
        var notebook = new Notebook(owner, "My Notebook", null);
        var conversation = new Conversation(notebook);

        assertThat(conversation.getNotebook()).isEqualTo(notebook);
        assertThat(conversation.getActiveSources()).isEmpty();
    }

    @Test
    void exposesGeneratedIdAndCreatedAt() {
        var owner = new User("cognito-sub", "user@test.com", "Test User");
        var notebook = new Notebook(owner, "My Notebook", null);
        var conversation = new Conversation(notebook);
        var id = UUID.randomUUID();
        var createdAt = Instant.now();

        ReflectionTestUtils.setField(conversation, "id", id);
        ReflectionTestUtils.setField(conversation, "createdAt", createdAt);

        assertThat(conversation.getId()).isEqualTo(id);
        assertThat(conversation.getCreatedAt()).isEqualTo(createdAt);
    }

    @Test
    void allowsAddingActiveSources() {
        var owner = new User("cognito-sub", "user@test.com", "Test User");
        var notebook = new Notebook(owner, "My Notebook", null);
        var source = new Source(notebook, "doc.pdf", SourceType.FILE, "some/key", null);
        var conversation = new Conversation(notebook);

        conversation.getActiveSources().add(source);

        assertThat(conversation.getActiveSources()).containsExactly(source);
    }
}
