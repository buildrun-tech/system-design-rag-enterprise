package tech.buildrun.notebooklm.dto;

import tech.buildrun.notebooklm.entity.ConversationMessage;
import tech.buildrun.notebooklm.entity.MessageRole;

import java.time.Instant;
import java.util.UUID;

public record ConversationMessageResponse(
        UUID id,
        MessageRole role,
        String content,
        Instant createdAt
) {

    public static ConversationMessageResponse from(ConversationMessage message) {
        return new ConversationMessageResponse(
                message.getId(),
                message.getRole(),
                message.getContent(),
                message.getCreatedAt()
        );
    }
}
