package tech.buildrun.notebooklm.service;

import io.awspring.cloud.s3.S3Template;
import io.awspring.cloud.sqs.operations.SqsTemplate;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import tech.buildrun.notebooklm.dto.IngestionMessage;
import tech.buildrun.notebooklm.dto.SourceResponse;
import tech.buildrun.notebooklm.entity.Notebook;
import tech.buildrun.notebooklm.entity.Source;
import tech.buildrun.notebooklm.entity.SourceType;
import tech.buildrun.notebooklm.exception.NotebookNotFoundException;
import tech.buildrun.notebooklm.exception.SourceNotFoundException;
import tech.buildrun.notebooklm.exception.UnsupportedFileTypeException;
import tech.buildrun.notebooklm.repository.NotebookRepository;
import tech.buildrun.notebooklm.repository.SourceRepository;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class SourceService {

    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of("pdf", "docx", "md");

    private final SourceRepository sourceRepository;
    private final NotebookRepository notebookRepository;
    private final S3Template s3Template;
    private final SqsTemplate sqsTemplate;
    private final VectorStore vectorStore;
    private final String bucketName;
    private final String ingestionQueueUrl;

    public SourceService(SourceRepository sourceRepository, NotebookRepository notebookRepository,
                          S3Template s3Template, SqsTemplate sqsTemplate, VectorStore vectorStore,
                          @Value("${aws.s3.bucket-name}") String bucketName,
                          @Value("${aws.sqs.ingestion-queue-url}") String ingestionQueueUrl) {
        this.sourceRepository = sourceRepository;
        this.notebookRepository = notebookRepository;
        this.s3Template = s3Template;
        this.sqsTemplate = sqsTemplate;
        this.vectorStore = vectorStore;
        this.bucketName = bucketName;
        this.ingestionQueueUrl = ingestionQueueUrl;
    }

    @Transactional
    public SourceResponse upload(UUID notebookId, UUID ownerId, MultipartFile file) {
        Notebook notebook = notebookRepository.findByIdAndOwnerId(notebookId, ownerId)
                .orElseThrow(NotebookNotFoundException::new);

        String filename = file.getOriginalFilename();
        if (filename == null || !SUPPORTED_EXTENSIONS.contains(extensionOf(filename))) {
            throw new UnsupportedFileTypeException(filename);
        }

        Source source = sourceRepository.saveAndFlush(new Source(notebook, filename, SourceType.FILE, null, null));

        String s3Key = "%s/%s/%s/%s".formatted(ownerId, notebookId, source.getId(), filename);
        try {
            s3Template.upload(bucketName, s3Key, file.getInputStream(), null);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        source.setS3Key(s3Key);

        sqsTemplate.send(ingestionQueueUrl, new IngestionMessage(source.getId()));

        return SourceResponse.from(source);
    }

    public List<SourceResponse> listByNotebook(UUID notebookId, UUID ownerId) {
        notebookRepository.findByIdAndOwnerId(notebookId, ownerId).orElseThrow(NotebookNotFoundException::new);
        return sourceRepository.findByNotebookId(notebookId).stream().map(SourceResponse::from).toList();
    }

    public SourceResponse get(UUID notebookId, UUID sourceId, UUID ownerId) {
        return SourceResponse.from(getOwnedOrThrow(notebookId, sourceId, ownerId));
    }

    @Transactional
    public void delete(UUID notebookId, UUID sourceId, UUID ownerId) {
        Source source = getOwnedOrThrow(notebookId, sourceId, ownerId);
        if (source.getS3Key() != null) {
            s3Template.deleteObject(bucketName, source.getS3Key());
        }
        vectorStore.delete("source_id == '%s'".formatted(sourceId));
        sourceRepository.delete(source);
    }

    private Source getOwnedOrThrow(UUID notebookId, UUID sourceId, UUID ownerId) {
        notebookRepository.findByIdAndOwnerId(notebookId, ownerId).orElseThrow(NotebookNotFoundException::new);
        return sourceRepository.findByIdAndNotebookId(sourceId, notebookId).orElseThrow(SourceNotFoundException::new);
    }

    private static String extensionOf(String filename) {
        int dotIndex = filename.lastIndexOf('.');
        return dotIndex < 0 ? "" : filename.substring(dotIndex + 1).toLowerCase();
    }
}
