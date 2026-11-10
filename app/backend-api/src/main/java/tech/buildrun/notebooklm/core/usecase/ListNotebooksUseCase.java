package tech.buildrun.notebooklm.core.usecase;

import tech.buildrun.notebooklm.core.domain.notebook.Notebook;
import tech.buildrun.notebooklm.core.domain.shared.PageQuery;
import tech.buildrun.notebooklm.core.domain.shared.PageResult;
import tech.buildrun.notebooklm.core.port.in.ListNotebooksPort;
import tech.buildrun.notebooklm.core.port.out.persistence.NotebookRepositoryPort;

import java.util.UUID;

public class ListNotebooksUseCase implements ListNotebooksPort {

    private final NotebookRepositoryPort notebooks;

    public ListNotebooksUseCase(NotebookRepositoryPort notebooks) {
        this.notebooks = notebooks;
    }

    @Override
    public PageResult<Notebook> execute(UUID ownerId, PageQuery pageQuery) {
        return notebooks.findByOwnerId(ownerId, pageQuery);
    }
}
