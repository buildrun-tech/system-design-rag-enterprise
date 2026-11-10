package tech.buildrun.notebooklm.core.usecase;

import tech.buildrun.notebooklm.core.domain.notebook.Notebook;
import tech.buildrun.notebooklm.core.port.in.CreateNotebookPort;
import tech.buildrun.notebooklm.core.port.out.persistence.NotebookRepositoryPort;

import java.util.UUID;

public class CreateNotebookUseCase implements CreateNotebookPort {

    private final NotebookRepositoryPort notebooks;

    public CreateNotebookUseCase(NotebookRepositoryPort notebooks) {
        this.notebooks = notebooks;
    }

    @Override
    public Notebook execute(UUID ownerId, String name, String description) {
        return notebooks.save(Notebook.create(ownerId, name, description));
    }
}
