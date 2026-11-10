package tech.buildrun.notebooklm.core.usecase;

import tech.buildrun.notebooklm.core.domain.exception.NotebookNotFoundException;
import tech.buildrun.notebooklm.core.domain.notebook.Notebook;
import tech.buildrun.notebooklm.core.port.in.GetNotebookPort;
import tech.buildrun.notebooklm.core.port.out.persistence.NotebookRepositoryPort;
import tech.buildrun.notebooklm.core.port.out.persistence.SourceRepositoryPort;

import java.util.UUID;

public class GetNotebookUseCase implements GetNotebookPort {

    private final NotebookRepositoryPort notebooks;
    private final SourceRepositoryPort sources;

    public GetNotebookUseCase(NotebookRepositoryPort notebooks, SourceRepositoryPort sources) {
        this.notebooks = notebooks;
        this.sources = sources;
    }

    @Override
    public NotebookDetail execute(UUID notebookId, UUID ownerId) {
        Notebook notebook = notebooks.findByIdAndOwnerId(notebookId, ownerId)
                .orElseThrow(NotebookNotFoundException::new);
        return new NotebookDetail(notebook, sources.findByNotebookId(notebookId));
    }
}
