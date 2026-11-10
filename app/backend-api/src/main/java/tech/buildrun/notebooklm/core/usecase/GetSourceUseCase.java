package tech.buildrun.notebooklm.core.usecase;

import tech.buildrun.notebooklm.core.domain.exception.NotebookNotFoundException;
import tech.buildrun.notebooklm.core.domain.exception.SourceNotFoundException;
import tech.buildrun.notebooklm.core.domain.source.Source;
import tech.buildrun.notebooklm.core.port.in.GetSourcePort;
import tech.buildrun.notebooklm.core.port.out.persistence.NotebookRepositoryPort;
import tech.buildrun.notebooklm.core.port.out.persistence.SourceRepositoryPort;

import java.util.UUID;

public class GetSourceUseCase implements GetSourcePort {

    private final SourceRepositoryPort sourceRepository;
    private final NotebookRepositoryPort notebookRepository;

    public GetSourceUseCase(SourceRepositoryPort sourceRepository, NotebookRepositoryPort notebookRepository) {
        this.sourceRepository = sourceRepository;
        this.notebookRepository = notebookRepository;
    }

    @Override
    public Source get(UUID notebookId, UUID sourceId, UUID ownerId) {
        notebookRepository.findByIdAndOwnerId(notebookId, ownerId).orElseThrow(NotebookNotFoundException::new);
        return sourceRepository.findByIdAndNotebookId(sourceId, notebookId).orElseThrow(SourceNotFoundException::new);
    }
}
