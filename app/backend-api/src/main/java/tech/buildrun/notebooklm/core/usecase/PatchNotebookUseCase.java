package tech.buildrun.notebooklm.core.usecase;

import tech.buildrun.notebooklm.core.domain.exception.NotebookNotFoundException;
import tech.buildrun.notebooklm.core.domain.notebook.Notebook;
import tech.buildrun.notebooklm.core.port.in.PatchNotebookPort;
import tech.buildrun.notebooklm.core.port.out.persistence.NotebookRepositoryPort;

import java.util.UUID;

public class PatchNotebookUseCase implements PatchNotebookPort {

    private final NotebookRepositoryPort notebooks;

    public PatchNotebookUseCase(NotebookRepositoryPort notebooks) {
        this.notebooks = notebooks;
    }

    @Override
    public Notebook execute(UUID notebookId, UUID ownerId, String name, String description) {
        Notebook notebook = notebooks.findByIdAndOwnerId(notebookId, ownerId)
                .orElseThrow(NotebookNotFoundException::new);
        notebook.patch(name, description);
        return notebooks.save(notebook);
    }
}
