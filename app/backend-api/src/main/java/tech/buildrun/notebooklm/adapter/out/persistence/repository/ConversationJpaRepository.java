package tech.buildrun.notebooklm.adapter.out.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import tech.buildrun.notebooklm.adapter.out.persistence.entity.ConversationJpaEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ConversationJpaRepository extends JpaRepository<ConversationJpaEntity, UUID> {

    @Query("select c from ConversationJpaEntity c join NotebookJpaEntity n on n.id = c.notebookId "
            + "where c.id = :id and n.ownerId = :ownerId")
    Optional<ConversationJpaEntity> findByIdAndOwnerId(@Param("id") UUID id, @Param("ownerId") UUID ownerId);

    @Query("select c from ConversationJpaEntity c join NotebookJpaEntity n on n.id = c.notebookId "
            + "where c.notebookId = :notebookId and n.ownerId = :ownerId order by c.createdAt desc")
    List<ConversationJpaEntity> findByNotebookIdAndOwnerId(@Param("notebookId") UUID notebookId,
                                                           @Param("ownerId") UUID ownerId);
}
