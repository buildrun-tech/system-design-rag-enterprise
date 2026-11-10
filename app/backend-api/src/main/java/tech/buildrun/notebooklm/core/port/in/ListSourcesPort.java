package tech.buildrun.notebooklm.core.port.in;

import tech.buildrun.notebooklm.core.domain.source.Source;

import java.util.List;
import java.util.UUID;

public interface ListSourcesPort {

    List<Source> listByNotebook(UUID notebookId, UUID ownerId);
}
