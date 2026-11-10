package tech.buildrun.notebooklm.core.usecase;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tech.buildrun.notebooklm.core.domain.exception.SourceNotFoundException;
import tech.buildrun.notebooklm.core.domain.source.DocumentChunk;
import tech.buildrun.notebooklm.core.domain.source.Source;
import tech.buildrun.notebooklm.core.port.in.IngestSourcePort;
import tech.buildrun.notebooklm.core.port.out.DocumentEnrichmentPort;
import tech.buildrun.notebooklm.core.port.out.ChunkStorePort;
import tech.buildrun.notebooklm.core.port.out.DocumentExtractionPort;
import tech.buildrun.notebooklm.core.port.out.FileStoragePort;
import tech.buildrun.notebooklm.core.port.out.persistence.SourceRepositoryPort;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.UUID;

public class IngestSourceUseCase implements IngestSourcePort {

    private static final Logger log = LoggerFactory.getLogger(IngestSourceUseCase.class);

    private final SourceRepositoryPort sourceRepository;
    private final FileStoragePort fileStorage;
    private final DocumentExtractionPort extraction;
    private final DocumentEnrichmentPort enrichment;
    private final ChunkStorePort chunkStore;

    public IngestSourceUseCase(SourceRepositoryPort sourceRepository, FileStoragePort fileStorage,
                               DocumentExtractionPort extraction, DocumentEnrichmentPort enrichment,
                               ChunkStorePort chunkStore) {
        this.sourceRepository = sourceRepository;
        this.fileStorage = fileStorage;
        this.extraction = extraction;
        this.enrichment = enrichment;
        this.chunkStore = chunkStore;
    }

    @Override
    public void ingest(UUID sourceId, Runnable onProgress) {
        try {
            run(sourceId, onProgress);
        } catch (SourceNotFoundException e) {
            throw e;
        } catch (Exception e) {
            log.error("Transactional Source ingestion failed: sourceId={}", sourceId, e);
            RuntimeException wrapped = new RuntimeException(e);
            markFailed(sourceId, wrapped.getMessage());
            throw wrapped;
        }
    }

    private void run(UUID sourceId, Runnable onProgress) {
        Source source = sourceRepository.findById(sourceId).orElseThrow(SourceNotFoundException::new);
        if (source.isReady()) {
            log.info("Source already READY, skipping: sourceId={}", sourceId);
            return;
        }
        source.markProcessing();
        sourceRepository.save(source);

        List<DocumentChunk> chunks;
        try (InputStream content = fileStorage.download(source.s3Key())) {
            chunks = extraction.extractAndSplit(content, source.name());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        List<DocumentChunk> enriched = enrichment.enrich(chunks, onProgress);

        log.info("Source ingestion: sourceId={} s3Key={} chunks={}", sourceId, source.s3Key(), enriched.size());
        chunkStore.deleteBySourceId(sourceId);
        chunkStore.index(sourceId, enriched);
        source.markReady();
        sourceRepository.save(source);
    }

    private void markFailed(UUID sourceId, String errorMessage) {
        sourceRepository.findById(sourceId).filter(source -> !source.isReady()).ifPresent(source -> {
            source.markFailed(errorMessage);
            sourceRepository.save(source);
        });
    }
}
