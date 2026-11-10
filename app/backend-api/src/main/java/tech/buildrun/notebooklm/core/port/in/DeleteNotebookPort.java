package tech.buildrun.notebooklm.core.port.in;

import java.util.UUID;

public interface DeleteNotebookPort {

    void execute(UUID notebookId, UUID ownerId);
}
