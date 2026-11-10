package tech.buildrun.notebooklm.core.port.out;

import tech.buildrun.notebooklm.core.domain.source.DocumentChunk;

import java.util.List;
import java.util.UUID;

public interface ChunkStorePort {

    void index(UUID sourceId, List<DocumentChunk> chunks);

    void deleteBySourceId(UUID sourceId);
}
