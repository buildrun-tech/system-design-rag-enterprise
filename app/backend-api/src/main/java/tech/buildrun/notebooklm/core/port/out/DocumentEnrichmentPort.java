package tech.buildrun.notebooklm.core.port.out;

import tech.buildrun.notebooklm.core.domain.source.DocumentChunk;

import java.util.List;

public interface DocumentEnrichmentPort {

    List<DocumentChunk> enrich(List<DocumentChunk> chunks, Runnable onChunkEnriched);
}
