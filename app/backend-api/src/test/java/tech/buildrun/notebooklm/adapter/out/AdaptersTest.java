package tech.buildrun.notebooklm.adapter.out;

import io.awspring.cloud.s3.S3Resource;
import io.awspring.cloud.s3.S3Template;
import io.awspring.cloud.sqs.operations.SqsTemplate;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.MDC;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import tech.buildrun.notebooklm.adapter.out.ai.TikaDocumentExtractionAdapter;
import tech.buildrun.notebooklm.adapter.out.queue.IngestionMessage;
import tech.buildrun.notebooklm.adapter.out.queue.SqsIngestionQueueAdapter;
import tech.buildrun.notebooklm.adapter.out.queue.SqsMessagePublisherAdapter;
import tech.buildrun.notebooklm.adapter.out.storage.S3FileStorageAdapter;
import tech.buildrun.notebooklm.adapter.out.vector.PgVectorChunkStoreAdapter;
import tech.buildrun.notebooklm.core.domain.outbox.OutboxEvent;
import tech.buildrun.notebooklm.core.domain.outbox.OutboxEventType;
import tech.buildrun.notebooklm.core.domain.outbox.OutboxStatus;
import tech.buildrun.notebooklm.core.domain.source.DocumentChunk;
import tech.buildrun.notebooklm.core.domain.source.IngestionRequest;
import tech.buildrun.notebooklm.core.port.out.persistence.OutboxRepositoryPort;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdaptersTest {

    @Test
    void s3StoresDownloadsAndDeletesInBucket() throws Exception {
        S3Template s3 = mock(S3Template.class);
        S3Resource resource = mock(S3Resource.class);
        InputStream stream = new ByteArrayInputStream("x".getBytes());
        when(s3.download("bucket", "k")).thenReturn(resource);
        when(resource.getInputStream()).thenReturn(stream);
        S3FileStorageAdapter adapter = new S3FileStorageAdapter(s3, "bucket");

        adapter.store("k", stream);
        assertThat(adapter.download("k")).isSameAs(stream);
        adapter.delete("k");

        verify(s3).upload("bucket", "k", stream, null);
        verify(s3).deleteObject("bucket", "k");
    }

    @Test
    void ingestionQueueWritesOutboxEventWithRequestId() {
        OutboxRepositoryPort outbox = mock(OutboxRepositoryPort.class);
        Clock clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
        UUID sourceId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        MDC.put("requestId", "req-1");
        try {
            new SqsIngestionQueueAdapter(outbox, clock).enqueue(new IngestionRequest(sourceId, userId));
        } finally {
            MDC.clear();
        }

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outbox).save(captor.capture());
        OutboxEvent event = captor.getValue();
        assertThat(event.type()).isEqualTo(OutboxEventType.INGESTION_REQUESTED);
        assertThat(event.status()).isEqualTo(OutboxStatus.PENDING);
        assertThat(event.aggregateId()).isEqualTo(sourceId);
        assertThat(event.payload()).containsEntry("sourceId", sourceId.toString())
                .containsEntry("userId", userId.toString()).containsEntry("requestId", "req-1");
    }

    @Test
    void sqsPublisherSendsIngestionMessageFromOutboxPayload() {
        SqsTemplate sqs = mock(SqsTemplate.class);
        UUID sourceId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        OutboxEvent event = OutboxEvent.create(OutboxEventType.INGESTION_REQUESTED, sourceId,
                Map.of("sourceId", sourceId.toString(), "userId", userId.toString(), "requestId", "req-1"),
                Instant.now());

        new SqsMessagePublisherAdapter(sqs, "queue").publish(event);

        verify(sqs).send("queue", new IngestionMessage(sourceId, "req-1", userId));
    }

    @Test
    void vectorStoreTagsChunksAndDeletesByFilter() {
        VectorStore store = mock(VectorStore.class);
        PgVectorChunkStoreAdapter adapter = new PgVectorChunkStoreAdapter(store, "model-x");
        UUID sourceId = UUID.randomUUID();

        adapter.index(sourceId, List.of(new DocumentChunk("t", Map.of("chunk_index", 0))));
        adapter.deleteBySourceId(sourceId);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Document>> captor = ArgumentCaptor.forClass(List.class);
        verify(store).add(captor.capture());
        assertThat(captor.getValue().get(0).getMetadata())
                .containsEntry("source_id", sourceId.toString())
                .containsEntry("embedding_model", "model-x")
                .containsEntry("chunk_index", 0);
        verify(store).delete("source_id == '%s'".formatted(sourceId));
    }

    @Test
    void tikaExtractsAndSplitsMarkdown() {
        List<DocumentChunk> chunks = new TikaDocumentExtractionAdapter()
                .extractAndSplit(new ByteArrayInputStream("hello world".getBytes()), "doc.md");

        assertThat(chunks).isNotEmpty();
        assertThat(chunks.get(0).text()).contains("hello world");
        assertThat(chunks.get(0).metadata()).containsKey("chunk_index");
    }
}
