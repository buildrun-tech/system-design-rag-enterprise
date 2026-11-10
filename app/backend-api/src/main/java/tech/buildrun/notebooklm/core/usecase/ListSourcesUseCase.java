package tech.buildrun.notebooklm.core.usecase;

import tech.buildrun.notebooklm.core.domain.exception.NotebookNotFoundException;
import tech.buildrun.notebooklm.core.domain.source.Source;
import tech.buildrun.notebooklm.core.port.in.ListSourcesPort;
import tech.buildrun.notebooklm.core.port.out.persistence.NotebookRepositoryPort;
import tech.buildrun.notebooklm.core.port.out.persistence.SourceRepositoryPort;

import java.util.List;
import java.util.UUID;

public class ListSourcesUseCase implements ListSourcesPort {

    private final SourceRepositoryPort sourceRepository;
    private final NotebookRepositoryPort notebookRepository;

    public ListSourcesUseCase(SourceRepositoryPort sourceRepository, NotebookRepositoryPort notebookRepository) {
        this.sourceRepository = sourceRepository;
        this.notebookRepository = notebookRepository;
    }

    @Override
    public List<Source> listByNotebook(UUID notebookId, UUID ownerId) {
        notebookRepository.findByIdAndOwnerId(notebookId, ownerId).orElseThrow(NotebookNotFoundException::new);
        return sourceRepository.findByNotebookId(notebookId);
    }
}
