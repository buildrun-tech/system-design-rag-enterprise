package tech.buildrun.notebooklm.core.usecase;

import tech.buildrun.notebooklm.core.domain.conversation.ConversationMessage;
import tech.buildrun.notebooklm.core.domain.exception.ConversationNotFoundException;
import tech.buildrun.notebooklm.core.port.in.ListMessagesPort;
import tech.buildrun.notebooklm.core.port.out.persistence.ConversationMessageRepositoryPort;
import tech.buildrun.notebooklm.core.port.out.persistence.ConversationRepositoryPort;

import java.util.List;
import java.util.UUID;

public class ListMessagesUseCase implements ListMessagesPort {

    private final ConversationRepositoryPort conversations;
    private final ConversationMessageRepositoryPort messages;

    public ListMessagesUseCase(ConversationRepositoryPort conversations,
                               ConversationMessageRepositoryPort messages) {
        this.conversations = conversations;
        this.messages = messages;
    }

    @Override
    public List<ConversationMessage> execute(UUID conversationId, UUID ownerId) {
        conversations.findByIdAndOwnerId(conversationId, ownerId).orElseThrow(ConversationNotFoundException::new);
        return messages.findAllByConversationIdAndOwnerId(conversationId, ownerId);
    }
}
