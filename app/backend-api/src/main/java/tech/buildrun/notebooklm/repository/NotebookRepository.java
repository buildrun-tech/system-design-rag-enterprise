package tech.buildrun.notebooklm.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import tech.buildrun.notebooklm.entity.Notebook;

import java.util.Optional;
import java.util.UUID;

public interface NotebookRepository extends JpaRepository<Notebook, UUID> {

    Optional<Notebook> findByIdAndOwner_Id(UUID id, UUID ownerId);

    Page<Notebook> findByOwner_Id(UUID ownerId, Pageable pageable);
}
