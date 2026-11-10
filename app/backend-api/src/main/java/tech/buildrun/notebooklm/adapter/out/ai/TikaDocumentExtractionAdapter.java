package tech.buildrun.notebooklm.adapter.out.ai;

import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.core.io.InputStreamResource;
import org.springframework.stereotype.Component;
import tech.buildrun.notebooklm.core.domain.source.DocumentChunk;
import tech.buildrun.notebooklm.core.port.out.DocumentExtractionPort;

import java.io.InputStream;
import java.util.List;

@Component
public class TikaDocumentExtractionAdapter implements DocumentExtractionPort {

    private static final int CHUNK_SIZE_TOKENS = 512;

    @Override
    public List<DocumentChunk> extractAndSplit(InputStream content, String filename) {
        InputStreamResource resource = new InputStreamResource(content) {
            @Override
            public String getFilename() {
                return filename;
            }
        };
        List<Document> extracted = new TikaDocumentReader(resource).get();
        return TokenTextSplitter.builder().withChunkSize(CHUNK_SIZE_TOKENS).build().apply(extracted).stream()
                .map(chunk -> new DocumentChunk(chunk.getText(), chunk.getMetadata()))
                .toList();
    }
}
