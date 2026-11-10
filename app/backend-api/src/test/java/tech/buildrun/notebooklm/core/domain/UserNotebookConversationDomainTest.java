package tech.buildrun.notebooklm.core.domain;

import org.junit.jupiter.api.Test;
import tech.buildrun.notebooklm.core.domain.conversation.CitedSource;
import tech.buildrun.notebooklm.core.domain.conversation.Conversation;
import tech.buildrun.notebooklm.core.domain.conversation.ConversationMessage;
import tech.buildrun.notebooklm.core.domain.conversation.MessageRole;
import tech.buildrun.notebooklm.core.domain.exception.InvalidSourceIdsException;
import tech.buildrun.notebooklm.core.domain.notebook.Notebook;
import tech.buildrun.notebooklm.core.domain.source.Source;
import tech.buildrun.notebooklm.core.domain.source.SourceStatus;
import tech.buildrun.notebooklm.core.domain.source.SourceType;
import tech.buildrun.notebooklm.core.domain.user.User;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserNotebookConversationDomainTest {

    @Test
    void userExposesCreateValuesAndNormalizesEmail() {
        User user = User.create("cognito-sub", "User@Test.COM", "Test User");

        assertThat(user.cognitoSub()).isEqualTo("cognito-sub");
        assertThat(user.email()).isEqualTo("user@test.com");
        assertThat(user.name()).isEqualTo("Test User");
        assertThat(user.id()).isNull();
    }

    @Test
    void userRestoreKeepsIdAndCreatedAt() {
        UUID id = UUID.randomUUID();
        Instant createdAt = Instant.now();

        User user = User.restore(id, "sub", "u@test.com", "U", createdAt);

        assertThat(user.id()).isEqualTo(id);
        assertThat(user.createdAt()).isEqualTo(createdAt);
    }

    @Test
    void userReattachChangesCognitoSub() {
        User user = User.create("old", "u@test.com", "U");

        user.reattachTo("new");

        assertThat(user.cognitoSub()).isEqualTo("new");
    }

    @Test
    void notebookExposesCreateValues() {
        UUID ownerId = UUID.randomUUID();

        Notebook notebook = Notebook.create(ownerId, "My Notebook", "description");

        assertThat(notebook.ownerId()).isEqualTo(ownerId);
        assertThat(notebook.name()).isEqualTo("My Notebook");
        assertThat(notebook.description()).isEqualTo("description");
        assertThat(notebook.isOwnedBy(ownerId)).isTrue();
        assertThat(notebook.isOwnedBy(UUID.randomUUID())).isFalse();
    }

    @Test
    void notebookRestoreKeepsIdAndTimestamps() {
        UUID id = UUID.randomUUID();
        Instant createdAt = Instant.now();
        Instant updatedAt = createdAt.plusSeconds(1);

        Notebook notebook = Notebook.restore(id, UUID.randomUUID(), "n", null, createdAt, updatedAt);

        assertThat(notebook.id()).isEqualTo(id);
        assertThat(notebook.createdAt()).isEqualTo(createdAt);
        assertThat(notebook.updatedAt()).isEqualTo(updatedAt);
    }

    @Test
    void notebookPatchChangesOnlyProvidedFields() {
        Notebook notebook = Notebook.create(UUID.randomUUID(), "name", "desc");

        notebook.patch("renamed", null);
        assertThat(notebook.name()).isEqualTo("renamed");
        assertThat(notebook.description()).isEqualTo("desc");

        notebook.patch(null, "new desc");
        assertThat(notebook.name()).isEqualTo("renamed");
        assertThat(notebook.description()).isEqualTo("new desc");
    }

    @Test
    void conversationStartsWithoutActiveSources() {
        UUID notebookId = UUID.randomUUID();

        Conversation conversation = Conversation.create(notebookId);

        assertThat(conversation.notebookId()).isEqualTo(notebookId);
        assertThat(conversation.activeSourceIds()).isEmpty();
        assertThat(conversation.hasActiveSources()).isFalse();
        assertThat(conversation.belongsTo(notebookId)).isTrue();
        assertThat(conversation.belongsTo(UUID.randomUUID())).isFalse();
    }

    @Test
    void conversationActivateReplacesActiveSources() {
        Conversation conversation = Conversation.create(UUID.randomUUID());
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();

        conversation.activate(Set.of(first));
        conversation.activate(Set.of(second));

        assertThat(conversation.activeSourceIds()).containsExactly(second);
        assertThat(conversation.hasActiveSources()).isTrue();
    }

    @Test
    void conversationActivateOnlyAcceptsSourcesOfItsNotebook() {
        UUID notebookId = UUID.randomUUID();
        Conversation conversation = Conversation.create(notebookId);
        UUID sourceId = UUID.randomUUID();
        Source own = Source.restore(sourceId, notebookId, "a.pdf", SourceType.FILE, "k", null,
                SourceStatus.READY, null, null);

        conversation.activateOnly(Set.of(sourceId), List.of(own));

        assertThat(conversation.activeSourceIds()).containsExactly(sourceId);
    }

    @Test
    void conversationActivateOnlyRejectsForeignOrMissingSources() {
        UUID notebookId = UUID.randomUUID();
        Conversation conversation = Conversation.create(notebookId);
        UUID foreignId = UUID.randomUUID();
        Source foreign = Source.restore(foreignId, UUID.randomUUID(), "b.pdf", SourceType.FILE, "k", null,
                SourceStatus.READY, null, null);

        assertThatThrownBy(() -> conversation.activateOnly(Set.of(foreignId), List.of(foreign)))
                .isInstanceOf(InvalidSourceIdsException.class);
        assertThatThrownBy(() -> conversation.activateOnly(Set.of(foreignId), List.of()))
                .isInstanceOf(InvalidSourceIdsException.class);
        assertThat(conversation.activeSourceIds()).isEmpty();
    }

    @Test
    void conversationAppendBuildsMessageForConversation() {
        UUID id = UUID.randomUUID();
        Conversation conversation = Conversation.restore(id, UUID.randomUUID(), Set.of(), Instant.now());

        ConversationMessage message = conversation.append(MessageRole.assistant, "hello");

        assertThat(message.conversationId()).isEqualTo(id);
        assertThat(message.role()).isEqualTo(MessageRole.assistant);
        assertThat(message.content()).isEqualTo("hello");
        assertThat(message.sources()).isEmpty();
        assertThat(message.isFromUser()).isFalse();
    }

    @Test
    void messageKeepsCitedSources() {
        Conversation conversation = Conversation.create(UUID.randomUUID());
        List<CitedSource> sources = List.of(new CitedSource(UUID.randomUUID(), "a.pdf", List.of()));

        ConversationMessage message = conversation.append(MessageRole.user, "hi", sources);

        assertThat(message.sources()).isEqualTo(sources);
        assertThat(message.isFromUser()).isTrue();
    }

    @Test
    void messagePreviewTruncatesAtFiftyCharacters() {
        Conversation conversation = Conversation.create(UUID.randomUUID());
        String exact = "a".repeat(50);

        assertThat(conversation.append(MessageRole.user, exact).preview()).isEqualTo(exact);
        assertThat(conversation.append(MessageRole.user, exact + "b").preview()).isEqualTo(exact + "...");
    }
}
