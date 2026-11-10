package tech.buildrun.notebooklm.core.usecase;

import tech.buildrun.notebooklm.core.domain.source.Source;
import tech.buildrun.notebooklm.core.port.in.DeleteSourcePort;
import tech.buildrun.notebooklm.core.port.in.GetSourcePort;
import tech.buildrun.notebooklm.core.port.out.ChunkStorePort;
import tech.buildrun.notebooklm.core.port.out.FileStoragePort;
import tech.buildrun.notebooklm.core.port.out.persistence.SourceRepositoryPort;

import java.util.UUID;

public class DeleteSourceUseCase implements DeleteSourcePort {

    private final GetSourcePort getSource;
    private final SourceRepositoryPort sourceRepository;
    private final FileStoragePort fileStorage;
    private final ChunkStorePort chunkStore;

    public DeleteSourceUseCase(GetSourcePort getSource, SourceRepositoryPort sourceRepository,
                               FileStoragePort fileStorage, ChunkStorePort chunkStore) {
        this.getSource = getSource;
        this.sourceRepository = sourceRepository;
        this.fileStorage = fileStorage;
        this.chunkStore = chunkStore;
    }

    @Override
    public void delete(UUID notebookId, UUID sourceId, UUID ownerId) {
        Source source = getSource.get(notebookId, sourceId, ownerId);
        if (source.s3Key() != null) {
            fileStorage.delete(source.s3Key());
        }
        chunkStore.deleteBySourceId(sourceId);
        sourceRepository.delete(source);
    }
}
