package tech.buildrun.notebooklm.core.usecase;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tech.buildrun.notebooklm.core.domain.exception.NotebookNotFoundException;
import tech.buildrun.notebooklm.core.domain.exception.SourceNotFoundException;
import tech.buildrun.notebooklm.core.domain.exception.UnsupportedFileTypeException;
import tech.buildrun.notebooklm.core.domain.notebook.Notebook;
import tech.buildrun.notebooklm.core.domain.source.IngestionRequest;
import tech.buildrun.notebooklm.core.domain.source.Source;
import tech.buildrun.notebooklm.core.domain.source.SourceStatus;
import tech.buildrun.notebooklm.core.domain.source.SourceType;
import tech.buildrun.notebooklm.core.port.in.GetSourcePort;
import tech.buildrun.notebooklm.core.port.in.UploadSourceCommand;
import tech.buildrun.notebooklm.core.port.out.ChunkStorePort;
import tech.buildrun.notebooklm.core.port.out.FileStoragePort;
import tech.buildrun.notebooklm.core.port.out.IngestionQueuePort;
import tech.buildrun.notebooklm.core.port.out.persistence.NotebookRepositoryPort;
import tech.buildrun.notebooklm.core.port.out.persistence.SourceRepositoryPort;

import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SourceUseCasesTest {

    @Mock
    private SourceRepositoryPort sourceRepository;
    @Mock
    private NotebookRepositoryPort notebookRepository;
    @Mock
    private FileStoragePort fileStorage;
    @Mock
    private IngestionQueuePort ingestionQueue;
    @Mock
    private ChunkStorePort chunkStore;

    private final UUID notebookId = UUID.randomUUID();
    private final UUID ownerId = UUID.randomUUID();

    private void ownedNotebook() {
        when(notebookRepository.findByIdAndOwnerId(notebookId, ownerId))
                .thenReturn(Optional.of(Notebook.create(ownerId, "Notebook", null)));
    }

    private UploadSourceCommand command(String filename) {
        return new UploadSourceCommand(notebookId, ownerId, filename, "application/pdf", 7,
                new ByteArrayInputStream("content".getBytes()));
    }

    private UploadSourceUseCase upload() {
        return new UploadSourceUseCase(sourceRepository, notebookRepository, fileStorage, ingestionQueue);
    }

    private void saveAssignsId() {
        when(sourceRepository.save(any(Source.class))).thenAnswer(invocation -> {
            Source s = invocation.getArgument(0);
            return Source.restore(s.id() != null ? s.id() : SOURCE_ID, s.notebookId(), s.name(), s.type(), s.s3Key(),
                    s.url(), s.status(), s.errorMessage(), s.createdAt());
        });
    }

    private static final UUID SOURCE_ID = UUID.randomUUID();

    @Test
    void uploadStoresFileAndEnqueuesIngestion() {
        ownedNotebook();
        saveAssignsId();

        Source result = upload().upload(command("doc.pdf"));

        String key = ownerId + "/" + notebookId + "/" + SOURCE_ID + "/doc.pdf";
        assertThat(result.status()).isEqualTo(SourceStatus.PENDING);
        assertThat(result.s3Key()).isEqualTo(key);
        verify(fileStorage).store(org.mockito.ArgumentMatchers.eq(key), any());
        verify(ingestionQueue).enqueue(new IngestionRequest(SOURCE_ID, ownerId));
    }

    @Test
    void uploadAcceptsFilenameStartingWithDot() {
        ownedNotebook();
        saveAssignsId();

        assertThat(upload().upload(command(".pdf")).status()).isEqualTo(SourceStatus.PENDING);
    }

    @Test
    void uploadRejectsUnsupportedExtension() {
        ownedNotebook();

        assertThatThrownBy(() -> upload().upload(command("sheet.xlsx")))
                .isInstanceOf(UnsupportedFileTypeException.class);

        verify(sourceRepository, never()).save(any());
        verify(ingestionQueue, never()).enqueue(any());
    }

    @Test
    void uploadRejectsNullFilename() {
        ownedNotebook();

        assertThatThrownBy(() -> upload().upload(command(null))).isInstanceOf(UnsupportedFileTypeException.class);
    }

    @Test
    void uploadToForeignNotebookThrows() {
        when(notebookRepository.findByIdAndOwnerId(notebookId, ownerId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> upload().upload(command("doc.pdf"))).isInstanceOf(NotebookNotFoundException.class);
    }

    @Test
    void listReturnsNotebookSources() {
        ownedNotebook();
        Source source = Source.create(notebookId, "a.md", SourceType.FILE, null, null);
        when(sourceRepository.findByNotebookId(notebookId)).thenReturn(List.of(source));

        assertThat(new ListSourcesUseCase(sourceRepository, notebookRepository).listByNotebook(notebookId, ownerId))
                .containsExactly(source);
    }

    @Test
    void listForeignNotebookThrows() {
        when(notebookRepository.findByIdAndOwnerId(notebookId, ownerId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> new ListSourcesUseCase(sourceRepository, notebookRepository)
                .listByNotebook(notebookId, ownerId)).isInstanceOf(NotebookNotFoundException.class);
    }

    @Test
    void getUnknownSourceThrows() {
        ownedNotebook();
        UUID sourceId = UUID.randomUUID();
        when(sourceRepository.findByIdAndNotebookId(sourceId, notebookId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> new GetSourceUseCase(sourceRepository, notebookRepository)
                .get(notebookId, sourceId, ownerId)).isInstanceOf(SourceNotFoundException.class);
    }

    @Test
    void deleteRemovesFileChunksAndSource() {
        UUID sourceId = UUID.randomUUID();
        Source source = Source.restore(sourceId, notebookId, "doc.pdf", SourceType.FILE, "some/key", null,
                SourceStatus.READY, null, null);
        GetSourcePort getSource = (n, s, o) -> source;

        new DeleteSourceUseCase(getSource, sourceRepository, fileStorage, chunkStore)
                .delete(notebookId, sourceId, ownerId);

        InOrder order = inOrder(fileStorage, chunkStore, sourceRepository);
        order.verify(fileStorage).delete("some/key");
        order.verify(chunkStore).deleteBySourceId(sourceId);
        order.verify(sourceRepository).delete(source);
    }

    @Test
    void deleteWithoutFileSkipsStorage() {
        UUID sourceId = UUID.randomUUID();
        Source source = Source.restore(sourceId, notebookId, "p", SourceType.URL, null, "https://x",
                SourceStatus.PENDING, null, null);

        new DeleteSourceUseCase((n, s, o) -> source, sourceRepository, fileStorage, chunkStore)
                .delete(notebookId, sourceId, ownerId);

        verify(fileStorage, never()).delete(any());
        verify(sourceRepository).delete(source);
    }

    @Test
    void deleteUnknownSourceThrows() {
        ownedNotebook();
        UUID sourceId = UUID.randomUUID();
        when(sourceRepository.findByIdAndNotebookId(sourceId, notebookId)).thenReturn(Optional.empty());
        GetSourcePort getSource = new GetSourceUseCase(sourceRepository, notebookRepository);

        assertThatThrownBy(() -> new DeleteSourceUseCase(getSource, sourceRepository, fileStorage, chunkStore)
                .delete(notebookId, sourceId, ownerId)).isInstanceOf(SourceNotFoundException.class);
    }
}
