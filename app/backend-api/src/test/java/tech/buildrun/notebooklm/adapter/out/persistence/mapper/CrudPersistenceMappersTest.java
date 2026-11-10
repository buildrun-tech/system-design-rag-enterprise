package tech.buildrun.notebooklm.adapter.out.persistence.mapper;

import org.junit.jupiter.api.Test;
import tech.buildrun.notebooklm.adapter.out.persistence.entity.ConversationJpaEntity;
import tech.buildrun.notebooklm.adapter.out.persistence.entity.ConversationMessageJpaEntity;
import tech.buildrun.notebooklm.adapter.out.persistence.entity.NotebookJpaEntity;
import tech.buildrun.notebooklm.adapter.out.persistence.entity.UserJpaEntity;
import tech.buildrun.notebooklm.core.domain.conversation.CitedChunk;
import tech.buildrun.notebooklm.core.domain.conversation.CitedSource;
import tech.buildrun.notebooklm.core.domain.conversation.Conversation;
import tech.buildrun.notebooklm.core.domain.conversation.ConversationMessage;
import tech.buildrun.notebooklm.core.domain.conversation.MessageRole;
import tech.buildrun.notebooklm.core.domain.notebook.Notebook;
import tech.buildrun.notebooklm.core.domain.user.User;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CrudPersistenceMappersTest {

    private final Instant now = Instant.now();

    @Test
    void userRoundTrip() {
        User user = User.restore(UUID.randomUUID(), "sub", "u@test.com", "U", now);

        UserJpaEntity entity = UserMapper.toEntity(user);
        User back = UserMapper.toDomain(entity);

        assertThat(entity.getCognitoSub()).isEqualTo("sub");
        assertThat(back).usingRecursiveComparison().isEqualTo(user);
    }

    @Test
    void notebookRoundTrip() {
        Notebook notebook = Notebook.restore(UUID.randomUUID(), UUID.randomUUID(), "n", "d", now, now.plusSeconds(1));

        NotebookJpaEntity entity = NotebookMapper.toEntity(notebook);
        Notebook back = NotebookMapper.toDomain(entity);

        assertThat(entity.getOwnerId()).isEqualTo(notebook.ownerId());
        assertThat(back).usingRecursiveComparison().isEqualTo(notebook);
    }

    @Test
    void conversationRoundTripKeepsActiveSources() {
        Set<UUID> active = Set.of(UUID.randomUUID(), UUID.randomUUID());
        Conversation conversation = Conversation.restore(UUID.randomUUID(), UUID.randomUUID(), active, now);

        ConversationJpaEntity entity = ConversationMapper.toEntity(conversation);
        Conversation back = ConversationMapper.toDomain(entity);

        assertThat(entity.getActiveSourceIds()).isEqualTo(active);
        assertThat(back.activeSourceIds()).isEqualTo(active);
        assertThat(back.notebookId()).isEqualTo(conversation.notebookId());
        assertThat(back.createdAt()).isEqualTo(now);
    }

    @Test
    void messageRoundTripKeepsSources() {
        List<CitedSource> sources = List.of(
                new CitedSource(UUID.randomUUID(), "a.pdf", List.of(new CitedChunk(1, "snippet"))));
        ConversationMessage message = ConversationMessage.restore(UUID.randomUUID(), UUID.randomUUID(),
                MessageRole.assistant, "answer", sources, now);

        ConversationMessageJpaEntity entity = ConversationMessageMapper.toEntity(message);
        ConversationMessage back = ConversationMessageMapper.toDomain(entity);

        assertThat(entity.getConversationId()).isEqualTo(message.conversationId());
        assertThat(back).usingRecursiveComparison().isEqualTo(message);
    }
}
