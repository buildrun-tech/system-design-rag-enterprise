package tech.buildrun.notebooklm.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import tech.buildrun.notebooklm.entity.ConversationMessage;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ConversationMessageRepository extends JpaRepository<ConversationMessage, UUID> {

    @Query("select m from ConversationMessage m "
            + "where m.conversation.id = :conversationId and m.conversation.notebook.owner.id = :ownerId "
            + "order by m.createdAt asc")
    List<ConversationMessage> findAllByConversationIdAndOwnerId(@Param("conversationId") UUID conversationId,
                                                                 @Param("ownerId") UUID ownerId);

    Optional<ConversationMessage> findFirstByConversation_IdOrderByCreatedAtAsc(UUID conversationId);
}
