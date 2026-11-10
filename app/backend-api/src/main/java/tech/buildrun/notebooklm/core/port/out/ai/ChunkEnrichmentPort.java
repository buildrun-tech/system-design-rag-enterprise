package tech.buildrun.notebooklm.core.port.out.ai;

import tech.buildrun.notebooklm.core.domain.chunk.ChunkText;
import tech.buildrun.notebooklm.core.domain.chunk.EnrichedChunk;

import java.util.List;

public interface ChunkEnrichmentPort {

    List<EnrichedChunk> enrich(List<ChunkText> chunks, Runnable onChunkEnriched);

    default List<EnrichedChunk> enrich(List<ChunkText> chunks) {
        return enrich(chunks, () -> { });
    }
}
