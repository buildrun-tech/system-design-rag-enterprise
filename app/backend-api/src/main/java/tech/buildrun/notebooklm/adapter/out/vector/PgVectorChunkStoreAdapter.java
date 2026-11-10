package tech.buildrun.notebooklm.adapter.out.vector;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tech.buildrun.notebooklm.core.domain.source.DocumentChunk;
import tech.buildrun.notebooklm.core.port.out.ChunkStorePort;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class PgVectorChunkStoreAdapter implements ChunkStorePort {

    private final VectorStore vectorStore;
    private final String embeddingModel;

    public PgVectorChunkStoreAdapter(VectorStore vectorStore,
                                     @Value("${spring.ai.openai.embedding.options.model}") String embeddingModel) {
        this.vectorStore = vectorStore;
        this.embeddingModel = embeddingModel;
    }

    @Override
    public void index(UUID sourceId, List<DocumentChunk> chunks) {
        vectorStore.add(chunks.stream().map(chunk -> toDocument(sourceId, chunk)).toList());
    }

    @Override
    public void deleteBySourceId(UUID sourceId) {
        vectorStore.delete("source_id == '%s'".formatted(sourceId));
    }

    private Document toDocument(UUID sourceId, DocumentChunk chunk) {
        Map<String, Object> metadata = new HashMap<>(chunk.metadata());
        metadata.put("source_id", sourceId.toString());
        metadata.put("embedding_model", embeddingModel);
        return new Document(chunk.text(), metadata);
    }
}
