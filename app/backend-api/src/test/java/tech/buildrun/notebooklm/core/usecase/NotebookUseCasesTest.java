package tech.buildrun.notebooklm.core.usecase;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tech.buildrun.notebooklm.core.domain.exception.NotebookNotFoundException;
import tech.buildrun.notebooklm.core.domain.notebook.Notebook;
import tech.buildrun.notebooklm.core.domain.shared.PageQuery;
import tech.buildrun.notebooklm.core.domain.shared.PageResult;
import tech.buildrun.notebooklm.core.domain.source.Source;
import tech.buildrun.notebooklm.core.domain.source.SourceType;
import tech.buildrun.notebooklm.core.port.in.GetNotebookPort.NotebookDetail;
import tech.buildrun.notebooklm.core.port.out.persistence.NotebookRepositoryPort;
import tech.buildrun.notebooklm.core.port.out.persistence.SourceRepositoryPort;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotebookUseCasesTest {

    private final UUID ownerId = UUID.randomUUID();
    private final UUID notebookId = UUID.randomUUID();

    @Mock
    private NotebookRepositoryPort notebooks;

    @Mock
    private SourceRepositoryPort sources;

    @Test
    void createSavesNotebookOwnedByUser() {
        when(notebooks.save(any(Notebook.class))).thenAnswer(i -> i.getArgument(0));

        Notebook created = new CreateNotebookUseCase(notebooks).execute(ownerId, "name", "desc");

        assertThat(created.isOwnedBy(ownerId)).isTrue();
        assertThat(created.name()).isEqualTo("name");
        assertThat(created.description()).isEqualTo("desc");
    }

    @Test
    void patchUpdatesOnlyProvidedFieldsAndSaves() {
        Notebook notebook = Notebook.create(ownerId, "name", "desc");
        when(notebooks.findByIdAndOwnerId(notebookId, ownerId)).thenReturn(Optional.of(notebook));
        when(notebooks.save(notebook)).thenReturn(notebook);

        Notebook result = new PatchNotebookUseCase(notebooks).execute(notebookId, ownerId, "renamed", null);

        assertThat(result.name()).isEqualTo("renamed");
        assertThat(result.description()).isEqualTo("desc");
    }

    @Test
    void patchRejectsNotebookNotOwned() {
        when(notebooks.findByIdAndOwnerId(notebookId, ownerId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> new PatchNotebookUseCase(notebooks).execute(notebookId, ownerId, "x", null))
                .isInstanceOf(NotebookNotFoundException.class);
        verify(notebooks, never()).save(any());
    }

    @Test
    void getReturnsNotebookWithItsSources() {
        Notebook notebook = Notebook.create(ownerId, "name", null);
        Source source = Source.create(notebookId, "doc.pdf", SourceType.FILE, "key", null);
        when(notebooks.findByIdAndOwnerId(notebookId, ownerId)).thenReturn(Optional.of(notebook));
        when(sources.findByNotebookId(notebookId)).thenReturn(List.of(source));

        NotebookDetail detail = new GetNotebookUseCase(notebooks, sources).execute(notebookId, ownerId);

        assertThat(detail.notebook()).isSameAs(notebook);
        assertThat(detail.sources()).containsExactly(source);
    }

    @Test
    void getRejectsNotebookNotOwned() {
        when(notebooks.findByIdAndOwnerId(notebookId, ownerId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> new GetNotebookUseCase(notebooks, sources).execute(notebookId, ownerId))
                .isInstanceOf(NotebookNotFoundException.class);
    }

    @Test
    void listDelegatesToRepository() {
        PageQuery query = new PageQuery(0, 20);
        PageResult<Notebook> page = new PageResult<>(List.of(), 0, 20, 0, 0);
        when(notebooks.findByOwnerId(ownerId, query)).thenReturn(page);

        assertThat(new ListNotebooksUseCase(notebooks).execute(ownerId, query)).isSameAs(page);
    }

    @Test
    void deleteRemovesOwnedNotebook() {
        Notebook notebook = Notebook.create(ownerId, "name", null);
        when(notebooks.findByIdAndOwnerId(notebookId, ownerId)).thenReturn(Optional.of(notebook));

        new DeleteNotebookUseCase(notebooks).execute(notebookId, ownerId);

        verify(notebooks).delete(notebook);
    }

    @Test
    void deleteRejectsNotebookNotOwned() {
        when(notebooks.findByIdAndOwnerId(notebookId, ownerId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> new DeleteNotebookUseCase(notebooks).execute(notebookId, ownerId))
                .isInstanceOf(NotebookNotFoundException.class);
        verify(notebooks, never()).delete(any());
    }
}
