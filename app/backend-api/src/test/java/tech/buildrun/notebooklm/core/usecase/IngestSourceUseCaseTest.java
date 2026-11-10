package tech.buildrun.notebooklm.core.usecase;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tech.buildrun.notebooklm.core.domain.exception.SourceNotFoundException;
import tech.buildrun.notebooklm.core.domain.source.DocumentChunk;
import tech.buildrun.notebooklm.core.domain.source.Source;
import tech.buildrun.notebooklm.core.domain.source.SourceStatus;
import tech.buildrun.notebooklm.core.domain.source.SourceType;
import tech.buildrun.notebooklm.core.port.out.DocumentEnrichmentPort;
import tech.buildrun.notebooklm.core.port.out.ChunkStorePort;
import tech.buildrun.notebooklm.core.port.out.DocumentExtractionPort;
import tech.buildrun.notebooklm.core.port.out.FileStoragePort;
import tech.buildrun.notebooklm.core.port.out.persistence.SourceRepositoryPort;

import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IngestSourceUseCaseTest {

    @Mock
    private SourceRepositoryPort sourceRepository;
    @Mock
    private FileStoragePort fileStorage;
    @Mock
    private DocumentExtractionPort extraction;
    @Mock
    private DocumentEnrichmentPort enrichment;
    @Mock
    private ChunkStorePort chunkStore;

    private final UUID sourceId = UUID.randomUUID();
    private final List<DocumentChunk> chunks = List.of(new DocumentChunk("hello", Map.of()));

    private IngestSourceUseCase useCase() {
        return new IngestSourceUseCase(sourceRepository, fileStorage, extraction, enrichment, chunkStore);
    }

    private Source source(SourceStatus status) {
        return Source.restore(sourceId, UUID.randomUUID(), "doc.md", SourceType.FILE, "some/key", null, status,
                null, null);
    }

    private void stubPipeline() {
        when(fileStorage.download("some/key")).thenReturn(new ByteArrayInputStream("hello".getBytes()));
        when(extraction.extractAndSplit(any(), anyString())).thenReturn(chunks);
    }

    @Test
    void ingestMarksReadyAndIndexesEnrichedChunks() {
        Source source = source(SourceStatus.PENDING);
        when(sourceRepository.findById(sourceId)).thenReturn(Optional.of(source));
        stubPipeline();
        List<DocumentChunk> enriched = List.of(new DocumentChunk("hello", Map.of("excerpt_keywords", "a")));
        when(enrichment.enrich(anyList(), any(Runnable.class))).thenReturn(enriched);

        useCase().ingest(sourceId, () -> { });

        assertThat(source.status()).isEqualTo(SourceStatus.READY);
        InOrder order = inOrder(chunkStore);
        order.verify(chunkStore).deleteBySourceId(sourceId);
        order.verify(chunkStore).index(sourceId, enriched);
        verify(sourceRepository, times(2)).save(source);
    }

    @Test
    void ingestPassesProgressCallbackToEnrichment() {
        when(sourceRepository.findById(sourceId)).thenReturn(Optional.of(source(SourceStatus.PENDING)));
        stubPipeline();
        when(enrichment.enrich(anyList(), any(Runnable.class))).thenAnswer(invocation -> {
            ((Runnable) invocation.getArgument(1)).run();
            return invocation.getArgument(0);
        });
        int[] ticks = {0};

        useCase().ingest(sourceId, () -> ticks[0]++);

        assertThat(ticks[0]).isEqualTo(1);
    }

    @Test
    void ingestSkipsSourceAlreadyReady() {
        when(sourceRepository.findById(sourceId)).thenReturn(Optional.of(source(SourceStatus.READY)));

        useCase().ingest(sourceId, () -> { });

        verify(fileStorage, never()).download(any());
        verify(chunkStore, never()).index(any(), anyList());
        verify(chunkStore, never()).deleteBySourceId(any());
    }

    @Test
    void ingestMarksFailedAndRethrowsWhenEnrichmentFails() {
        Source source = source(SourceStatus.PENDING);
        when(sourceRepository.findById(sourceId)).thenReturn(Optional.of(source));
        stubPipeline();
        when(enrichment.enrich(anyList(), any(Runnable.class))).thenThrow(new IllegalStateException("llm down"));

        assertThatThrownBy(() -> useCase().ingest(sourceId, () -> { }))
                .isInstanceOf(RuntimeException.class)
                .hasCauseInstanceOf(IllegalStateException.class);

        assertThat(source.status()).isEqualTo(SourceStatus.FAILED);
        assertThat(source.errorMessage()).contains("llm down");
        verify(chunkStore, never()).index(any(), anyList());
    }

    @Test
    void ingestMarksFailedWhenStorageFails() {
        Source source = source(SourceStatus.PENDING);
        when(sourceRepository.findById(sourceId)).thenReturn(Optional.of(source));
        when(fileStorage.download("some/key")).thenThrow(new java.io.UncheckedIOException(new java.io.IOException("s3")));

        assertThatThrownBy(() -> useCase().ingest(sourceId, () -> { })).isInstanceOf(RuntimeException.class);

        assertThat(source.status()).isEqualTo(SourceStatus.FAILED);
        assertThat(source.errorMessage()).isNotNull();
    }

    @Test
    void ingestRethrowsWhenSourceMissing() {
        when(sourceRepository.findById(sourceId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase().ingest(sourceId, () -> { })).isInstanceOf(SourceNotFoundException.class);

        verify(sourceRepository, never()).save(any());
    }
}
