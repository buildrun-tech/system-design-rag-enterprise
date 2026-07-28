package tech.buildrun.notebooklm.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import tech.buildrun.notebooklm.entity.ConversationMessage;

import java.util.UUID;

public interface ConversationMessageRepository extends JpaRepository<ConversationMessage, UUID> {
}
