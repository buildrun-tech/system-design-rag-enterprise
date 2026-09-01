package tech.buildrun.notebooklm.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import tech.buildrun.notebooklm.entity.Conversation;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ConversationRepository extends JpaRepository<Conversation, UUID> {

    Optional<Conversation> findByIdAndNotebookOwnerId(UUID id, UUID ownerId);

    List<Conversation> findByNotebookIdAndNotebookOwnerIdOrderByCreatedAtDesc(UUID notebookId, UUID ownerId);
}
