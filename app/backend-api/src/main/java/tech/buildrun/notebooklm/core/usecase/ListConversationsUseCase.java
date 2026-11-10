package tech.buildrun.notebooklm.core.usecase;

import tech.buildrun.notebooklm.core.domain.conversation.ConversationMessage;
import tech.buildrun.notebooklm.core.domain.exception.NotebookNotFoundException;
import tech.buildrun.notebooklm.core.port.in.ListConversationsPort;
import tech.buildrun.notebooklm.core.port.out.persistence.ConversationMessageRepositoryPort;
import tech.buildrun.notebooklm.core.port.out.persistence.ConversationRepositoryPort;
import tech.buildrun.notebooklm.core.port.out.persistence.NotebookRepositoryPort;

import java.util.List;
import java.util.UUID;

public class ListConversationsUseCase implements ListConversationsPort {

    private final NotebookRepositoryPort notebooks;
    private final ConversationRepositoryPort conversations;
    private final ConversationMessageRepositoryPort messages;

    public ListConversationsUseCase(NotebookRepositoryPort notebooks, ConversationRepositoryPort conversations,
                                    ConversationMessageRepositoryPort messages) {
        this.notebooks = notebooks;
        this.conversations = conversations;
        this.messages = messages;
    }

    @Override
    public List<ConversationSummary> execute(UUID notebookId, UUID ownerId) {
        notebooks.findByIdAndOwnerId(notebookId, ownerId).orElseThrow(NotebookNotFoundException::new);
        return conversations.findByNotebookIdAndOwnerIdOrderByCreatedAtDesc(notebookId, ownerId).stream()
                .map(conversation -> new ConversationSummary(conversation,
                        messages.findFirstByConversationIdOrderByCreatedAtAsc(conversation.id())
                                .map(ConversationMessage::preview)
                                .orElse(null)))
                .toList();
    }
}
