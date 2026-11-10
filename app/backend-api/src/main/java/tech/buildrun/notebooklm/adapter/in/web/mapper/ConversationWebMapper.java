package tech.buildrun.notebooklm.adapter.in.web.mapper;

import tech.buildrun.notebooklm.adapter.in.web.dto.ConversationDetailResponse;
import tech.buildrun.notebooklm.adapter.in.web.dto.ConversationMessageResponse;
import tech.buildrun.notebooklm.adapter.in.web.dto.ConversationResponse;
import tech.buildrun.notebooklm.core.domain.conversation.Conversation;
import tech.buildrun.notebooklm.core.domain.conversation.ConversationMessage;
import tech.buildrun.notebooklm.core.port.in.ListConversationsPort.ConversationSummary;

public final class ConversationWebMapper {

    private ConversationWebMapper() {
    }

    public static ConversationDetailResponse toDetailResponse(Conversation conversation) {
        return new ConversationDetailResponse(conversation.id(), conversation.notebookId(),
                conversation.activeSourceIds().stream().toList(), conversation.createdAt());
    }

    public static ConversationResponse toResponse(ConversationSummary summary) {
        Conversation conversation = summary.conversation();
        return new ConversationResponse(conversation.id(), conversation.notebookId(), conversation.createdAt(),
                summary.preview());
    }

    public static ConversationMessageResponse toResponse(ConversationMessage message) {
        return new ConversationMessageResponse(message.id(), message.role(), message.content(),
                message.sources(), message.createdAt());
    }
}
