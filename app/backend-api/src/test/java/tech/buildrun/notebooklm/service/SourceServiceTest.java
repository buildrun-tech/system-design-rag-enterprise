package tech.buildrun.notebooklm.service;

import io.awspring.cloud.s3.S3Resource;
import io.awspring.cloud.s3.S3Template;
import io.awspring.cloud.sqs.operations.SqsTemplate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import tech.buildrun.notebooklm.entity.Notebook;
import tech.buildrun.notebooklm.entity.Source;
import tech.buildrun.notebooklm.entity.SourceType;
import tech.buildrun.notebooklm.entity.User;
import tech.buildrun.notebooklm.exception.NotebookNotFoundException;
import tech.buildrun.notebooklm.exception.SourceNotFoundException;
import tech.buildrun.notebooklm.exception.UnsupportedFileTypeException;
import tech.buildrun.notebooklm.repository.NotebookRepository;
import tech.buildrun.notebooklm.repository.SourceRepository;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SourceServiceTest {

    @Mock
    private SourceRepository sourceRepository;

    @Mock
    private NotebookRepository notebookRepository;

    @Mock
    private S3Template s3Template;

    @Mock
    private SqsTemplate sqsTemplate;

    @Mock
    private VectorStore vectorStore;

    private SourceService newService() {
        return new SourceService(sourceRepository, notebookRepository, s3Template, sqsTemplate, vectorStore,
                "bucket", "queue-url");
    }

    @Test
    void uploadPersistsPendingUploadsToS3AndPublishesSqs() {
        User owner = new User("sub", "owner@test.com", "Owner");
        Notebook notebook = new Notebook(owner, "Notebook", null);
        UUID notebookId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();

        when(notebookRepository.findByIdAndOwner_Id(notebookId, ownerId)).thenReturn(Optional.of(notebook));
        when(sourceRepository.saveAndFlush(any(Source.class))).thenAnswer(invocation -> {
            Source source = invocation.getArgument(0);
            ReflectionTestUtils.setField(source, "id", UUID.randomUUID());
            return source;
        });
        when(s3Template.upload(any(), any(), any(), any())).thenReturn(mock(S3Resource.class));

        MockMultipartFile file = new MockMultipartFile("file", "doc.pdf", "application/pdf", "content".getBytes());

        var response = newService().upload(notebookId, ownerId, file);

        assertThat(response.name()).isEqualTo("doc.pdf");
        assertThat(response.status().name()).isEqualTo("PENDING");
        verify(s3Template).upload(any(), any(), any(), any());
        verify(sqsTemplate).send(any(), any(tech.buildrun.notebooklm.dto.IngestionMessage.class));

        var savedCaptor = org.mockito.ArgumentCaptor.forClass(Source.class);
        verify(sourceRepository).saveAndFlush(savedCaptor.capture());
        assertThat(savedCaptor.getValue().getS3Key())
                .isEqualTo(ownerId + "/" + notebookId + "/" + savedCaptor.getValue().getId() + "/doc.pdf");
    }

    @Test
    void uploadWithFilenameStartingWithDotIsTreatedAsSupportedExtension() {
        User owner = new User("sub", "owner@test.com", "Owner");
        Notebook notebook = new Notebook(owner, "Notebook", null);
        UUID notebookId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();

        when(notebookRepository.findByIdAndOwner_Id(notebookId, ownerId)).thenReturn(Optional.of(notebook));
        when(sourceRepository.saveAndFlush(any(Source.class))).thenAnswer(invocation -> {
            Source source = invocation.getArgument(0);
            ReflectionTestUtils.setField(source, "id", UUID.randomUUID());
            return source;
        });
        when(s3Template.upload(any(), any(), any(), any())).thenReturn(mock(S3Resource.class));

        MockMultipartFile file = new MockMultipartFile("file", ".pdf", "application/pdf", "content".getBytes());

        var response = newService().upload(notebookId, ownerId, file);

        assertThat(response.status().name()).isEqualTo("PENDING");
    }

    @Test
    void uploadWithUnsupportedExtensionThrows() {
        User owner = new User("sub", "owner@test.com", "Owner");
        Notebook notebook = new Notebook(owner, "Notebook", null);
        UUID notebookId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();

        when(notebookRepository.findByIdAndOwner_Id(notebookId, ownerId)).thenReturn(Optional.of(notebook));

        MockMultipartFile file = new MockMultipartFile("file", "sheet.xlsx", "application/vnd.ms-excel", "content".getBytes());

        assertThatThrownBy(() -> newService().upload(notebookId, ownerId, file))
                .isInstanceOf(UnsupportedFileTypeException.class);

        verify(sourceRepository, never()).saveAndFlush(any());
        verify(sqsTemplate, never()).send(any(), any());
    }

    @Test
    void uploadToNotebookOfAnotherOwnerThrows() {
        UUID notebookId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        when(notebookRepository.findByIdAndOwner_Id(notebookId, ownerId)).thenReturn(Optional.empty());

        MockMultipartFile file = new MockMultipartFile("file", "doc.pdf", "application/pdf", "content".getBytes());

        assertThatThrownBy(() -> newService().upload(notebookId, ownerId, file))
                .isInstanceOf(NotebookNotFoundException.class);
    }

    @Test
    void deleteRemovesS3ObjectVectorStoreEntriesAndSource() {
        User owner = new User("sub", "owner@test.com", "Owner");
        Notebook notebook = new Notebook(owner, "Notebook", null);
        UUID notebookId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID sourceId = UUID.randomUUID();
        Source source = new Source(notebook, "doc.pdf", SourceType.FILE, "some/key", null);
        ReflectionTestUtils.setField(source, "id", sourceId);

        when(notebookRepository.findByIdAndOwner_Id(notebookId, ownerId)).thenReturn(Optional.of(notebook));
        when(sourceRepository.findByIdAndNotebook_Id(sourceId, notebookId)).thenReturn(Optional.of(source));

        newService().delete(notebookId, sourceId, ownerId);

        verify(s3Template).deleteObject("bucket", "some/key");
        verify(vectorStore).delete("source_id == '" + sourceId + "'");
        verify(sourceRepository).delete(source);
    }

    @Test
    void deleteUnknownSourceThrows() {
        Notebook notebook = new Notebook(new User("sub", "owner@test.com", "Owner"), "Notebook", null);
        UUID notebookId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID sourceId = UUID.randomUUID();

        when(notebookRepository.findByIdAndOwner_Id(notebookId, ownerId)).thenReturn(Optional.of(notebook));
        when(sourceRepository.findByIdAndNotebook_Id(sourceId, notebookId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> newService().delete(notebookId, sourceId, ownerId))
                .isInstanceOf(SourceNotFoundException.class);
    }
}
