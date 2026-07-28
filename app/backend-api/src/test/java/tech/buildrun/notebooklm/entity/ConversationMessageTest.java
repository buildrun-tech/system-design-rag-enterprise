package tech.buildrun.notebooklm.entity;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ConversationMessageTest {

    @Test
    void exposesConstructorValuesThroughGetters() {
        var owner = new User("cognito-sub", "user@test.com", "Test User");
        var notebook = new Notebook(owner, "My Notebook", null);
        var conversation = new Conversation(notebook);
        var message = new ConversationMessage(conversation, MessageRole.assistant, "hello");

        assertThat(message.getConversation()).isEqualTo(conversation);
        assertThat(message.getRole()).isEqualTo(MessageRole.assistant);
        assertThat(message.getContent()).isEqualTo("hello");
    }

    @Test
    void exposesGeneratedIdAndCreatedAt() {
        var owner = new User("cognito-sub", "user@test.com", "Test User");
        var notebook = new Notebook(owner, "My Notebook", null);
        var conversation = new Conversation(notebook);
        var message = new ConversationMessage(conversation, MessageRole.user, "hi");
        var id = UUID.randomUUID();
        var createdAt = Instant.now();

        ReflectionTestUtils.setField(message, "id", id);
        ReflectionTestUtils.setField(message, "createdAt", createdAt);

        assertThat(message.getId()).isEqualTo(id);
        assertThat(message.getCreatedAt()).isEqualTo(createdAt);
    }
}
