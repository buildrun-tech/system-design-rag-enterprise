package tech.buildrun.notebooklm.core.port.out.persistence;

import tech.buildrun.notebooklm.core.domain.conversation.Conversation;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ConversationRepositoryPort {

    Optional<Conversation> findByIdAndOwnerId(UUID id, UUID ownerId);

    List<Conversation> findByNotebookIdAndOwnerIdOrderByCreatedAtDesc(UUID notebookId, UUID ownerId);

    Conversation save(Conversation conversation);

    void delete(Conversation conversation);
}
