package tech.buildrun.notebooklm.core.port.out.vector;

import tech.buildrun.notebooklm.core.domain.chunk.ChunkText;
import tech.buildrun.notebooklm.core.domain.chunk.EnrichedChunk;

import java.util.List;
import java.util.UUID;

public interface ChunkInsightsPort {

    List<ChunkText> findWithoutKeywords(List<UUID> sourceIds);

    void updateEnrichment(EnrichedChunk chunk);

    List<String> topKeywords(List<UUID> sourceIds, int limit);

    List<ChunkText> searchByTopics(List<UUID> sourceIds, List<String> topics, int chunksPerTopic);
}
