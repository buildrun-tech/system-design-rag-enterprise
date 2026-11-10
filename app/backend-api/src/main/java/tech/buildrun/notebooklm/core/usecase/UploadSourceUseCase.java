package tech.buildrun.notebooklm.core.usecase;

import tech.buildrun.notebooklm.core.domain.exception.NotebookNotFoundException;
import tech.buildrun.notebooklm.core.domain.exception.UnsupportedFileTypeException;
import tech.buildrun.notebooklm.core.domain.source.IngestionRequest;
import tech.buildrun.notebooklm.core.domain.source.Source;
import tech.buildrun.notebooklm.core.domain.source.SourceType;
import tech.buildrun.notebooklm.core.port.in.UploadSourceCommand;
import tech.buildrun.notebooklm.core.port.in.UploadSourcePort;
import tech.buildrun.notebooklm.core.port.out.FileStoragePort;
import tech.buildrun.notebooklm.core.port.out.IngestionQueuePort;
import tech.buildrun.notebooklm.core.port.out.persistence.NotebookRepositoryPort;
import tech.buildrun.notebooklm.core.port.out.persistence.SourceRepositoryPort;

import java.util.Set;

public class UploadSourceUseCase implements UploadSourcePort {

    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of("pdf", "docx", "md");

    private final SourceRepositoryPort sourceRepository;
    private final NotebookRepositoryPort notebookRepository;
    private final FileStoragePort fileStorage;
    private final IngestionQueuePort ingestionQueue;

    public UploadSourceUseCase(SourceRepositoryPort sourceRepository, NotebookRepositoryPort notebookRepository,
                               FileStoragePort fileStorage, IngestionQueuePort ingestionQueue) {
        this.sourceRepository = sourceRepository;
        this.notebookRepository = notebookRepository;
        this.fileStorage = fileStorage;
        this.ingestionQueue = ingestionQueue;
    }

    @Override
    public Source upload(UploadSourceCommand command) {
        notebookRepository.findByIdAndOwnerId(command.notebookId(), command.ownerId())
                .orElseThrow(NotebookNotFoundException::new);

        String filename = command.filename();
        if (filename == null || !SUPPORTED_EXTENSIONS.contains(extensionOf(filename))) {
            throw new UnsupportedFileTypeException(filename);
        }

        Source source = sourceRepository.save(Source.create(command.notebookId(), filename, SourceType.FILE, null, null));

        String s3Key = "%s/%s/%s/%s".formatted(command.ownerId(), command.notebookId(), source.id(), filename);
        fileStorage.store(s3Key, command.content());
        source.storedAt(s3Key);
        source = sourceRepository.save(source);

        ingestionQueue.enqueue(new IngestionRequest(source.id(), command.ownerId()));
        return source;
    }

    private static String extensionOf(String filename) {
        int dotIndex = filename.lastIndexOf('.');
        return dotIndex < 0 ? "" : filename.substring(dotIndex + 1).toLowerCase();
    }
}
