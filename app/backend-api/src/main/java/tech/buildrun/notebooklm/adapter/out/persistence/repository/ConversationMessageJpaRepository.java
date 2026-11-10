package tech.buildrun.notebooklm.adapter.out.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import tech.buildrun.notebooklm.adapter.out.persistence.entity.ConversationMessageJpaEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ConversationMessageJpaRepository extends JpaRepository<ConversationMessageJpaEntity, UUID> {

    @Query("select m from ConversationMessageJpaEntity m "
            + "join ConversationJpaEntity c on c.id = m.conversationId "
            + "join NotebookJpaEntity n on n.id = c.notebookId "
            + "where m.conversationId = :conversationId and n.ownerId = :ownerId "
            + "order by m.createdAt asc")
    List<ConversationMessageJpaEntity> findAllByConversationIdAndOwnerId(@Param("conversationId") UUID conversationId,
                                                                          @Param("ownerId") UUID ownerId);

    Optional<ConversationMessageJpaEntity> findFirstByConversationIdOrderByCreatedAtAsc(UUID conversationId);

    List<ConversationMessageJpaEntity> findTop10ByConversationIdOrderByCreatedAtDesc(UUID conversationId);
}
