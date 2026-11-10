package tech.buildrun.notebooklm.core.port.in;

import java.util.UUID;

public interface DeleteSourcePort {

    void delete(UUID notebookId, UUID sourceId, UUID ownerId);
}
