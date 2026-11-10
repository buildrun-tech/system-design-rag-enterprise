package tech.buildrun.notebooklm.core.port.out.persistence;

import tech.buildrun.notebooklm.core.domain.source.Source;
import tech.buildrun.notebooklm.core.domain.source.SourceStatus;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SourceRepositoryPort {

    Optional<Source> findById(UUID id);

    Optional<Source> findByIdAndNotebookId(UUID id, UUID notebookId);

    List<Source> findAllById(Collection<UUID> ids);

    List<Source> findByNotebookId(UUID notebookId);

    List<Source> findByNotebookIdAndStatus(UUID notebookId, SourceStatus status);

    Source save(Source source);

    void delete(Source source);
}
