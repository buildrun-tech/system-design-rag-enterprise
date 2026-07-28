package tech.buildrun.notebooklm.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import tech.buildrun.notebooklm.entity.Conversation;

import java.util.UUID;

public interface ConversationRepository extends JpaRepository<Conversation, UUID> {
}
