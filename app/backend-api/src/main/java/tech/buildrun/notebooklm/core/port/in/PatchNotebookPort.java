package tech.buildrun.notebooklm.core.port.in;

import tech.buildrun.notebooklm.core.domain.notebook.Notebook;

import java.util.UUID;

public interface PatchNotebookPort {

    Notebook execute(UUID notebookId, UUID ownerId, String name, String description);
}
