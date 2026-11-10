package tech.buildrun.notebooklm.core.port.in;

import tech.buildrun.notebooklm.core.domain.source.Source;

import java.util.UUID;

public interface GetSourcePort {

    Source get(UUID notebookId, UUID sourceId, UUID ownerId);
}
