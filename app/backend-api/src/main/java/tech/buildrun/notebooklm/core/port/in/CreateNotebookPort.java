package tech.buildrun.notebooklm.core.port.in;

import tech.buildrun.notebooklm.core.domain.notebook.Notebook;

import java.util.UUID;

public interface CreateNotebookPort {

    Notebook execute(UUID ownerId, String name, String description);
}
