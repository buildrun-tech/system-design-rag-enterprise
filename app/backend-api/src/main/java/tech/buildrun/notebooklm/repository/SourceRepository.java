package tech.buildrun.notebooklm.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import tech.buildrun.notebooklm.entity.Source;
import tech.buildrun.notebooklm.entity.SourceStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SourceRepository extends JpaRepository<Source, UUID> {

    List<Source> findByNotebookId(UUID notebookId);

    List<Source> findByNotebookIdAndStatus(UUID notebookId, SourceStatus status);

    Optional<Source> findByIdAndNotebookId(UUID id, UUID notebookId);
}
