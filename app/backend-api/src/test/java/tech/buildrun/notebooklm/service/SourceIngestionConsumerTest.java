package tech.buildrun.notebooklm.service;

import io.awspring.cloud.s3.S3Resource;
import io.awspring.cloud.s3.S3Template;
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

    private SourceIngestionConsumer newConsumer() {
        return new SourceIngestionConsumer(sourceRepository, s3Template, vectorStore, "bucket", "openai/text-embedding-3-large");
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

        newConsumer().process(new IngestionMessage(sourceId));

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
                .containsEntry("embedding_model", "openai/text-embedding-3-large");
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

        assertThatThrownBy(() -> newConsumer().process(new IngestionMessage(sourceId)))
                .isInstanceOf(RuntimeException.class);

        assertThat(source.getStatus()).isEqualTo(SourceStatus.FAILED);
        assertThat(source.getErrorMessage()).isNotNull();
        verify(vectorStore, never()).add(anyList());
    }

    @Test
    void processMarksFailedAndRethrowsWhenSourceMissing() {
        UUID sourceId = UUID.randomUUID();
        when(sourceRepository.findById(sourceId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> newConsumer().process(new IngestionMessage(sourceId)))
                .isInstanceOf(SourceNotFoundException.class);

        verify(vectorStore, never()).add(anyList());
    }
}
