package tech.buildrun.notebooklm.adapter.out.persistence.mapper;

import tech.buildrun.notebooklm.adapter.out.persistence.entity.ConversationJpaEntity;
import tech.buildrun.notebooklm.core.domain.conversation.Conversation;

public final class ConversationMapper {

    private ConversationMapper() {
    }

    public static Conversation toDomain(ConversationJpaEntity entity) {
        return Conversation.restore(entity.getId(), entity.getNotebookId(), entity.getActiveSourceIds(),
                entity.getCreatedAt());
    }

    public static ConversationJpaEntity toEntity(Conversation conversation) {
        return new ConversationJpaEntity(conversation.id(), conversation.notebookId(),
                conversation.activeSourceIds(), conversation.createdAt());
    }
}
