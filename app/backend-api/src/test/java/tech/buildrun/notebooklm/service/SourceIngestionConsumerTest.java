package tech.buildrun.notebooklm.service;

import io.awspring.cloud.s3.S3Resource;
import com.openai.core.http.Headers;
import com.openai.errors.UnexpectedStatusCodeException;
import io.awspring.cloud.s3.S3Template;
import io.awspring.cloud.sqs.listener.Visibility;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import tech.buildrun.notebooklm.dto.IngestionMessage;
import tech.buildrun.notebooklm.entity.Notebook;
import tech.buildrun.notebooklm.entity.Source;
import tech.buildrun.notebooklm.entity.SourceStatus;
import tech.buildrun.notebooklm.entity.SourceType;
import tech.buildrun.notebooklm.entity.User;
import tech.buildrun.notebooklm.exception.SourceNotFoundException;
import tech.buildrun.notebooklm.repository.SourceRepository;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SourceIngestionConsumerTest {

    @Mock
    private SourceRepository sourceRepository;

    @Mock
    private S3Template s3Template;

    @Mock
    private VectorStore vectorStore;

    @Mock
    private ChunkEnrichmentService chunkEnrichmentService;

    private SourceIngestionConsumer newConsumer() {
        return new SourceIngestionConsumer(sourceRepository, s3Template, vectorStore, chunkEnrichmentService,
                "bucket", "openai/text-embedding-3-large");
    }

    @Test
    void processSetsReadyAndAddsChunksToVectorStoreOnSuccess() throws Exception {
        User owner = new User("sub", "owner@test.com", "Owner");
        Notebook notebook = new Notebook(owner, "Notebook", null);
        Source source = spy(new Source(notebook, "doc.md", SourceType.FILE, "some/key", null));
        UUID sourceId = UUID.randomUUID();
        org.springframework.test.util.ReflectionTestUtils.setField(source, "id", sourceId);

        when(sourceRepository.findById(sourceId)).thenReturn(Optional.of(source));

        S3Resource resource = mock(S3Resource.class);
        when(s3Template.download("bucket", "some/key")).thenReturn(resource);
        when(resource.getInputStream()).thenReturn(new ByteArrayInputStream("hello world".getBytes()));
        when(resource.getFilename()).thenReturn("doc.md");

        when(chunkEnrichmentService.enrich(anyList(), any(Runnable.class))).thenAnswer(invocation -> {
            List<Document> chunks = invocation.getArgument(0);
            return chunks.stream().map(chunk -> chunk.mutate().metadata("excerpt_keywords", "a, b").build()).toList();
        });

        newConsumer().process(new IngestionMessage(sourceId), null);

        InOrder order = inOrder(source);
        order.verify(source).setStatus(SourceStatus.PROCESSING);
        order.verify(source).setStatus(SourceStatus.READY);
        assertThat(source.getStatus()).isEqualTo(SourceStatus.READY);

        ArgumentCaptor<List<Document>> captor = ArgumentCaptor.forClass(List.class);
        verify(vectorStore).add(captor.capture());
        List<Document> added = captor.getValue();
        assertThat(added).isNotEmpty();
        assertThat(added.get(0).getMetadata())
                .containsEntry("source_id", sourceId.toString())
                .containsEntry("embedding_model", "openai/text-embedding-3-large")
                .containsEntry("excerpt_keywords", "a, b");
    }

    @Test
    void processMarksSourceFailedWhenEnrichmentFails() throws Exception {
        User owner = new User("sub", "owner@test.com", "Owner");
        Notebook notebook = new Notebook(owner, "Notebook", null);
        Source source = new Source(notebook, "doc.md", SourceType.FILE, "some/key", null);
        UUID sourceId = UUID.randomUUID();
        org.springframework.test.util.ReflectionTestUtils.setField(source, "id", sourceId);

        when(sourceRepository.findById(sourceId)).thenReturn(Optional.of(source));
        S3Resource resource = mock(S3Resource.class);
        when(s3Template.download("bucket", "some/key")).thenReturn(resource);
        when(resource.getInputStream()).thenReturn(new ByteArrayInputStream("hello world".getBytes()));
        when(resource.getFilename()).thenReturn("doc.md");
        when(chunkEnrichmentService.enrich(anyList(), any(Runnable.class))).thenThrow(new IllegalStateException("llm down"));

        assertThatThrownBy(() -> newConsumer().process(new IngestionMessage(sourceId), null))
                .isInstanceOf(RuntimeException.class);

        assertThat(source.getStatus()).isEqualTo(SourceStatus.FAILED);
        verify(vectorStore, never()).add(anyList());
    }

    @Test
    void processMarksSourceFailedAndRethrowsWhenIngestionThrows() throws Exception {
        User owner = new User("sub", "owner@test.com", "Owner");
        Notebook notebook = new Notebook(owner, "Notebook", null);
        Source source = new Source(notebook, "doc.md", SourceType.FILE, "some/key", null);
        UUID sourceId = UUID.randomUUID();
        org.springframework.test.util.ReflectionTestUtils.setField(source, "id", sourceId);

        when(sourceRepository.findById(sourceId)).thenReturn(Optional.of(source));

        S3Resource resource = mock(S3Resource.class);
        when(s3Template.download("bucket", "some/key")).thenReturn(resource);
        when(resource.getInputStream()).thenThrow(new IOException("s3 unavailable"));

        assertThatThrownBy(() -> newConsumer().process(new IngestionMessage(sourceId), null))
                .isInstanceOf(RuntimeException.class);

        assertThat(source.getStatus()).isEqualTo(SourceStatus.FAILED);
        assertThat(source.getErrorMessage()).isNotNull();
        verify(vectorStore, never()).add(anyList());
    }

    @Test
    void processMarksFailedAndRethrowsWhenSourceMissing() {
        UUID sourceId = UUID.randomUUID();
        when(sourceRepository.findById(sourceId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> newConsumer().process(new IngestionMessage(sourceId), null))
                .isInstanceOf(SourceNotFoundException.class);

        verify(vectorStore, never()).add(anyList());
    }

    private Source newSource(UUID sourceId, SourceStatus status) {
        User owner = new User("sub", "owner@test.com", "Owner");
        Source source = new Source(new Notebook(owner, "Notebook", null), "doc.md", SourceType.FILE, "some/key", null);
        org.springframework.test.util.ReflectionTestUtils.setField(source, "id", sourceId);
        source.setStatus(status);
        return source;
    }

    private void stubDownload(String content) throws Exception {
        S3Resource resource = mock(S3Resource.class);
        when(s3Template.download("bucket", "some/key")).thenReturn(resource);
        when(resource.getInputStream()).thenReturn(new ByteArrayInputStream(content.getBytes()));
        when(resource.getFilename()).thenReturn("doc.md");
    }

    @Test
    void processSkipsSourceAlreadyReady() {
        UUID sourceId = UUID.randomUUID();
        Source source = newSource(sourceId, SourceStatus.READY);
        when(sourceRepository.findById(sourceId)).thenReturn(Optional.of(source));

        newConsumer().process(new IngestionMessage(sourceId), null);

        assertThat(source.getStatus()).isEqualTo(SourceStatus.READY);
        verify(s3Template, never()).download(any(), any());
        verify(vectorStore, never()).add(anyList());
        verify(vectorStore, never()).delete(any(String.class));
    }

    @Test
    void processReplacesExistingChunksBeforeAdding() throws Exception {
        UUID sourceId = UUID.randomUUID();
        when(sourceRepository.findById(sourceId)).thenReturn(Optional.of(newSource(sourceId, SourceStatus.PENDING)));
        stubDownload("hello world");
        when(chunkEnrichmentService.enrich(anyList(), any(Runnable.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        newConsumer().process(new IngestionMessage(sourceId), null);

        InOrder order = inOrder(vectorStore);
        order.verify(vectorStore).delete("source_id == '%s'".formatted(sourceId));
        order.verify(vectorStore).add(anyList());
    }

    @Test
    void processExtendsVisibilityTimeoutForEachEnrichedChunk() throws Exception {
        UUID sourceId = UUID.randomUUID();
        when(sourceRepository.findById(sourceId)).thenReturn(Optional.of(newSource(sourceId, SourceStatus.PENDING)));
        stubDownload("hello world");
        when(chunkEnrichmentService.enrich(anyList(), any(Runnable.class))).thenAnswer(invocation -> {
            Runnable heartbeat = invocation.getArgument(1);
            heartbeat.run();
            heartbeat.run();
            return invocation.getArgument(0);
        });
        Visibility visibility = mock(Visibility.class);

        newConsumer().process(new IngestionMessage(sourceId), visibility);

        verify(visibility, times(2)).changeTo(180);
    }

    @Test
    void processKeepsGoingWhenExtendingVisibilityFails() throws Exception {
        UUID sourceId = UUID.randomUUID();
        Source source = newSource(sourceId, SourceStatus.PENDING);
        when(sourceRepository.findById(sourceId)).thenReturn(Optional.of(source));
        stubDownload("hello world");
        when(chunkEnrichmentService.enrich(anyList(), any(Runnable.class))).thenAnswer(invocation -> {
            ((Runnable) invocation.getArgument(1)).run();
            return invocation.getArgument(0);
        });
        Visibility visibility = mock(Visibility.class);
        org.mockito.Mockito.doThrow(new IllegalStateException("sqs down")).when(visibility).changeTo(180);

        newConsumer().process(new IngestionMessage(sourceId), visibility);

        assertThat(source.getStatus()).isEqualTo(SourceStatus.READY);
    }

    @Test
    void processAcknowledgesWithoutRethrowingOnPermanentProviderError() throws Exception {
        UUID sourceId = UUID.randomUUID();
        Source source = newSource(sourceId, SourceStatus.PENDING);
        when(sourceRepository.findById(sourceId)).thenReturn(Optional.of(source));
        stubDownload("hello world");
        when(chunkEnrichmentService.enrich(anyList(), any(Runnable.class)))
                .thenThrow(providerError(402));

        newConsumer().process(new IngestionMessage(sourceId), null);

        assertThat(source.getStatus()).isEqualTo(SourceStatus.FAILED);
        verify(vectorStore, never()).add(anyList());
    }

    @Test
    void processRethrowsOnTransientProviderError() throws Exception {
        UUID sourceId = UUID.randomUUID();
        Source source = newSource(sourceId, SourceStatus.PENDING);
        when(sourceRepository.findById(sourceId)).thenReturn(Optional.of(source));
        stubDownload("hello world");
        when(chunkEnrichmentService.enrich(anyList(), any(Runnable.class)))
                .thenThrow(providerError(503));

        assertThatThrownBy(() -> newConsumer().process(new IngestionMessage(sourceId), null))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void markFailedNeverRegressesReadySource() {
        UUID sourceId = UUID.randomUUID();
        Source source = newSource(sourceId, SourceStatus.READY);
        when(sourceRepository.findById(sourceId)).thenReturn(Optional.of(source));

        newConsumer().markFailed(sourceId, "late duplicate failed");

        assertThat(source.getStatus()).isEqualTo(SourceStatus.READY);
        assertThat(source.getErrorMessage()).isNull();
    }

    private static UnexpectedStatusCodeException providerError(int status) {
        return UnexpectedStatusCodeException.builder().statusCode(status).headers(Headers.builder().build()).build();
    }
}
