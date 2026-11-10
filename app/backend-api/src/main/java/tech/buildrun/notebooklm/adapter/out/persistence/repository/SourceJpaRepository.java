package tech.buildrun.notebooklm.adapter.out.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import tech.buildrun.notebooklm.adapter.out.persistence.entity.SourceJpaEntity;
import tech.buildrun.notebooklm.core.domain.source.SourceStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SourceJpaRepository extends JpaRepository<SourceJpaEntity, UUID> {

    @Query("select s from SourceJpaEntity s where s.notebookId = :notebookId")
    List<SourceJpaEntity> findByNotebookId(@Param("notebookId") UUID notebookId);

    @Query("select s from SourceJpaEntity s where s.notebookId = :notebookId and s.status = :status")
    List<SourceJpaEntity> findByNotebookIdAndStatus(@Param("notebookId") UUID notebookId,
                                                    @Param("status") SourceStatus status);

    @Query("select s from SourceJpaEntity s where s.id = :id and s.notebookId = :notebookId")
    Optional<SourceJpaEntity> findByIdAndNotebookId(@Param("id") UUID id, @Param("notebookId") UUID notebookId);
}
