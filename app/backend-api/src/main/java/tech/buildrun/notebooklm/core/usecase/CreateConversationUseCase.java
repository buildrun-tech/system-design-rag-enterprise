package tech.buildrun.notebooklm.core.usecase;

import tech.buildrun.notebooklm.core.domain.conversation.Conversation;
import tech.buildrun.notebooklm.core.domain.exception.NotebookNotFoundException;
import tech.buildrun.notebooklm.core.domain.source.Source;
import tech.buildrun.notebooklm.core.domain.source.SourceStatus;
import tech.buildrun.notebooklm.core.port.in.CreateConversationPort;
import tech.buildrun.notebooklm.core.port.out.persistence.ConversationRepositoryPort;
import tech.buildrun.notebooklm.core.port.out.persistence.NotebookRepositoryPort;
import tech.buildrun.notebooklm.core.port.out.persistence.SourceRepositoryPort;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public class CreateConversationUseCase implements CreateConversationPort {

    private final NotebookRepositoryPort notebooks;
    private final SourceRepositoryPort sources;
    private final ConversationRepositoryPort conversations;

    public CreateConversationUseCase(NotebookRepositoryPort notebooks, SourceRepositoryPort sources,
                                     ConversationRepositoryPort conversations) {
        this.notebooks = notebooks;
        this.sources = sources;
        this.conversations = conversations;
    }

    @Override
    public Conversation execute(UUID notebookId, UUID ownerId, List<UUID> activeSourceIds) {
        notebooks.findByIdAndOwnerId(notebookId, ownerId).orElseThrow(NotebookNotFoundException::new);
        Conversation conversation = Conversation.create(notebookId);
        if (activeSourceIds == null || activeSourceIds.isEmpty()) {
            conversation.activate(readyIdsOf(notebookId));
        } else {
            Set<UUID> requested = Set.copyOf(activeSourceIds);
            conversation.activateOnly(requested, sources.findAllById(requested));
        }
        return conversations.save(conversation);
    }

    private Set<UUID> readyIdsOf(UUID notebookId) {
        return sources.findByNotebookIdAndStatus(notebookId, SourceStatus.READY).stream()
                .map(Source::id)
                .collect(Collectors.toSet());
    }
}
