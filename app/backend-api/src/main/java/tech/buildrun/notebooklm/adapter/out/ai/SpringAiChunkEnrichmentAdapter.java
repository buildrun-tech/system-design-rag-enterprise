package tech.buildrun.notebooklm.adapter.out.ai;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.document.Document;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.model.transformer.KeywordMetadataEnricher;
import org.springframework.ai.model.transformer.SummaryMetadataEnricher;
import org.springframework.ai.model.transformer.SummaryMetadataEnricher.SummaryType;
import org.springframework.stereotype.Component;
import tech.buildrun.notebooklm.core.domain.chunk.ChunkText;
import tech.buildrun.notebooklm.core.domain.chunk.EnrichedChunk;
import tech.buildrun.notebooklm.core.port.out.ai.ChunkEnrichmentPort;

import java.util.ArrayList;
import java.util.List;

@Component
public class SpringAiChunkEnrichmentAdapter implements ChunkEnrichmentPort {

    public static final String KEYWORDS_KEY = KeywordMetadataEnricher.EXCERPT_KEYWORDS_METADATA_KEY;
    public static final String SUMMARY_KEY = "section_summary";

    private static final int KEYWORD_COUNT = 5;

    private final KeywordMetadataEnricher keywordEnricher;
    private final SummaryMetadataEnricher summaryEnricher;

    public SpringAiChunkEnrichmentAdapter(ChatModel chatModel) {
        this.keywordEnricher = new KeywordMetadataEnricher(chatModel, KEYWORD_COUNT);
        this.summaryEnricher = new SummaryMetadataEnricher(chatModel, List.of(SummaryType.CURRENT),
                SummaryMetadataEnricher.DEFAULT_SUMMARY_EXTRACT_TEMPLATE, MetadataMode.NONE);
    }

    @Override
    public List<EnrichedChunk> enrich(List<ChunkText> chunks, Runnable onChunkEnriched) {
        List<EnrichedChunk> enriched = new ArrayList<>(chunks.size());
        for (ChunkText chunk : chunks) {
            Document source = Document.builder().id(chunk.id()).text(chunk.text()).build();
            List<Document> result = summaryEnricher.apply(keywordEnricher.apply(new ArrayList<>(List.of(source))));
            result.forEach(doc -> enriched.add(new EnrichedChunk(doc.getId(),
                    String.valueOf(doc.getMetadata().get(KEYWORDS_KEY)),
                    String.valueOf(doc.getMetadata().get(SUMMARY_KEY)))));
            onChunkEnriched.run();
        }
        return enriched;
    }
}
