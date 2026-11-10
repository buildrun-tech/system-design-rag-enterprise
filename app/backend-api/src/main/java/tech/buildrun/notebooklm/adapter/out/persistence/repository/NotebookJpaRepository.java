package tech.buildrun.notebooklm.adapter.out.persistence.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import tech.buildrun.notebooklm.adapter.out.persistence.entity.NotebookJpaEntity;

import java.util.Optional;
import java.util.UUID;

public interface NotebookJpaRepository extends JpaRepository<NotebookJpaEntity, UUID> {

    Optional<NotebookJpaEntity> findByIdAndOwnerId(UUID id, UUID ownerId);

    Page<NotebookJpaEntity> findByOwnerId(UUID ownerId, Pageable pageable);
}
