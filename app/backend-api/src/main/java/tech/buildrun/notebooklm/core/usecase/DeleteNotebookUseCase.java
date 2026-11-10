package tech.buildrun.notebooklm.core.usecase;

import tech.buildrun.notebooklm.core.domain.exception.NotebookNotFoundException;
import tech.buildrun.notebooklm.core.port.in.DeleteNotebookPort;
import tech.buildrun.notebooklm.core.port.out.persistence.NotebookRepositoryPort;

import java.util.UUID;

public class DeleteNotebookUseCase implements DeleteNotebookPort {

    private final NotebookRepositoryPort notebooks;

    public DeleteNotebookUseCase(NotebookRepositoryPort notebooks) {
        this.notebooks = notebooks;
    }

    @Override
    public void execute(UUID notebookId, UUID ownerId) {
        notebooks.delete(notebooks.findByIdAndOwnerId(notebookId, ownerId)
                .orElseThrow(NotebookNotFoundException::new));
    }
}
