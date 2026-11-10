package tech.buildrun.notebooklm.core.port.out.persistence;

import tech.buildrun.notebooklm.core.domain.conversation.ConversationMessage;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ConversationMessageRepositoryPort {

    List<ConversationMessage> findAllByConversationIdAndOwnerId(UUID conversationId, UUID ownerId);

    Optional<ConversationMessage> findFirstByConversationIdOrderByCreatedAtAsc(UUID conversationId);

    List<ConversationMessage> findTop10ByConversationIdOrderByCreatedAtDesc(UUID conversationId);

    ConversationMessage save(ConversationMessage message);
}
