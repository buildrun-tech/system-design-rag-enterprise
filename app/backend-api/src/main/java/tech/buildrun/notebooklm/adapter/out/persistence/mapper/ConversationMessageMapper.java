package tech.buildrun.notebooklm.adapter.out.persistence.mapper;

import tech.buildrun.notebooklm.adapter.out.persistence.entity.ConversationMessageJpaEntity;
import tech.buildrun.notebooklm.core.domain.conversation.ConversationMessage;

public final class ConversationMessageMapper {

    private ConversationMessageMapper() {
    }

    public static ConversationMessage toDomain(ConversationMessageJpaEntity entity) {
        return ConversationMessage.restore(entity.getId(), entity.getConversationId(), entity.getRole(),
                entity.getContent(), entity.getSources(), entity.getCreatedAt());
    }

    public static ConversationMessageJpaEntity toEntity(ConversationMessage message) {
        return new ConversationMessageJpaEntity(message.id(), message.conversationId(), message.role(),
                message.content(), message.sources(), message.createdAt());
    }
}
