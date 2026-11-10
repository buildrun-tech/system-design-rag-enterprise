package tech.buildrun.notebooklm.core.port.out.persistence;

import tech.buildrun.notebooklm.core.domain.notebook.Notebook;
import tech.buildrun.notebooklm.core.domain.shared.PageQuery;
import tech.buildrun.notebooklm.core.domain.shared.PageResult;

import java.util.Optional;
import java.util.UUID;

public interface NotebookRepositoryPort {

    Optional<Notebook> findByIdAndOwnerId(UUID id, UUID ownerId);

    PageResult<Notebook> findByOwnerId(UUID ownerId, PageQuery pageQuery);

    Notebook save(Notebook notebook);

    void delete(Notebook notebook);
}
