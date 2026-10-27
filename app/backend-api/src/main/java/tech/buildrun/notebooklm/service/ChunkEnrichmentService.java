package tech.buildrun.notebooklm.service;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.document.Document;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.model.transformer.KeywordMetadataEnricher;
import org.springframework.ai.model.transformer.SummaryMetadataEnricher;
import org.springframework.ai.model.transformer.SummaryMetadataEnricher.SummaryType;
import org.springframework.stereotype.Service;

import java.util.List;

/** Enriquece chunks com {@code excerpt_keywords} e {@code section_summary}; usado na ingestão e no backfill. */
@Service
public class ChunkEnrichmentService {

    static final String KEYWORDS_KEY = KeywordMetadataEnricher.EXCERPT_KEYWORDS_METADATA_KEY;
    static final String SUMMARY_KEY = "section_summary";

    private static final int KEYWORD_COUNT = 5;

    private final KeywordMetadataEnricher keywordEnricher;
    private final SummaryMetadataEnricher summaryEnricher;

    public ChunkEnrichmentService(ChatModel chatModel) {
        this.keywordEnricher = new KeywordMetadataEnricher(chatModel, KEYWORD_COUNT);
        // MetadataMode.NONE: o prompt do resumo não deve carregar source_id/keywords.
        this.summaryEnricher = new SummaryMetadataEnricher(chatModel, List.of(SummaryType.CURRENT),
                SummaryMetadataEnricher.DEFAULT_SUMMARY_EXTRACT_TEMPLATE, MetadataMode.NONE);
    }

    // ponytail: sequencial (2 chamadas LLM por chunk). Upgrade: lotes paralelos se a ingestão ficar lenta.
    public List<Document> enrich(List<Document> chunks) {
        return summaryEnricher.apply(keywordEnricher.apply(chunks));
    }
}
