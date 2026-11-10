package tech.buildrun.notebooklm.adapter.out.ai;

import org.springframework.stereotype.Component;
import tech.buildrun.notebooklm.core.domain.chunk.ChunkText;
import tech.buildrun.notebooklm.core.domain.chunk.EnrichedChunk;
import tech.buildrun.notebooklm.core.domain.source.DocumentChunk;
import tech.buildrun.notebooklm.core.port.out.DocumentEnrichmentPort;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

@Component
public class SpringAiDocumentEnrichmentAdapter implements DocumentEnrichmentPort {

    private final SpringAiChunkEnrichmentAdapter delegate;

    public SpringAiDocumentEnrichmentAdapter(SpringAiChunkEnrichmentAdapter delegate) {
        this.delegate = delegate;
    }

    @Override
    public List<DocumentChunk> enrich(List<DocumentChunk> chunks, Runnable onChunkEnriched) {
        List<ChunkText> texts = IntStream.range(0, chunks.size())
                .mapToObj(i -> new ChunkText(String.valueOf(i), chunks.get(i).text()))
                .toList();
        List<EnrichedChunk> enriched = delegate.enrich(texts, onChunkEnriched);
        return IntStream.range(0, chunks.size()).mapToObj(i -> {
            Map<String, Object> metadata = new HashMap<>(chunks.get(i).metadata());
            metadata.put(SpringAiChunkEnrichmentAdapter.KEYWORDS_KEY, enriched.get(i).keywords());
            metadata.put(SpringAiChunkEnrichmentAdapter.SUMMARY_KEY, enriched.get(i).summary());
            return new DocumentChunk(chunks.get(i).text(), metadata);
        }).toList();
    }
}
