package tech.buildrun.notebooklm.core.usecase;

import tech.buildrun.notebooklm.core.domain.conversation.Conversation;
import tech.buildrun.notebooklm.core.domain.exception.ConversationNotFoundException;
import tech.buildrun.notebooklm.core.port.in.DeleteConversationPort;
import tech.buildrun.notebooklm.core.port.out.persistence.ConversationRepositoryPort;

import java.util.UUID;

public class DeleteConversationUseCase implements DeleteConversationPort {

    private final ConversationRepositoryPort conversations;

    public DeleteConversationUseCase(ConversationRepositoryPort conversations) {
        this.conversations = conversations;
    }

    @Override
    public void execute(UUID notebookId, UUID conversationId, UUID ownerId) {
        Conversation conversation = conversations.findByIdAndOwnerId(conversationId, ownerId)
                .filter(c -> c.belongsTo(notebookId))
                .orElseThrow(ConversationNotFoundException::new);
        conversations.delete(conversation);
    }
}
