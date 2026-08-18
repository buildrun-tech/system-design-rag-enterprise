package tech.buildrun.notebooklm.service;

import io.awspring.cloud.s3.S3Template;
import io.awspring.cloud.sqs.annotation.SqsListener;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tech.buildrun.notebooklm.dto.IngestionMessage;
import tech.buildrun.notebooklm.entity.Source;
import tech.buildrun.notebooklm.entity.SourceStatus;
import tech.buildrun.notebooklm.exception.SourceNotFoundException;
import tech.buildrun.notebooklm.repository.SourceRepository;

import java.util.List;
import java.util.UUID;

// ponytail: sem essa guarda, contexto Spring que sobe sem fila configurada
// (testes, dev sem LocalStack) quebra no boot inteiro por causa do listener.
@Service
@ConditionalOnExpression("T(org.springframework.util.StringUtils).hasText('${aws.sqs.ingestion-queue-url:}')")
public class SourceIngestionConsumer {

    // ponytail: chunk sem overlap — TokenTextSplitter (Spring AI) nao suporta overlap.
    // Upgrade: splitter custom se overlap entre chunks virar necessidade real.
    private static final int CHUNK_SIZE_TOKENS = 512;

    private final SourceRepository sourceRepository;
    private final S3Template s3Template;
    private final VectorStore vectorStore;
    private final String bucketName;
    private final String embeddingModel;

    public SourceIngestionConsumer(SourceRepository sourceRepository, S3Template s3Template, VectorStore vectorStore,
                                    @Value("${aws.s3.bucket-name}") String bucketName,
                                    @Value("${spring.ai.openai.embedding.options.model}") String embeddingModel) {
        this.sourceRepository = sourceRepository;
        this.s3Template = s3Template;
        this.vectorStore = vectorStore;
        this.bucketName = bucketName;
        this.embeddingModel = embeddingModel;
    }

    @SqsListener("${aws.sqs.ingestion-queue-url}")
    public void process(IngestionMessage message) {
        try {
            ingest(message.sourceId());
        } catch (Exception e) {
            // marca FAILED em transacao propria: a transacao de ingest() ja fez
            // rollback, entao o status so fica visivel se persistido a parte.
            markFailed(message.sourceId(), e.getMessage());
            throw e;
        }
    }

    @Transactional
    void ingest(UUID sourceId) {
        Source source = sourceRepository.findById(sourceId).orElseThrow(SourceNotFoundException::new);
        source.setStatus(SourceStatus.PROCESSING);

        var resource = s3Template.download(bucketName, source.getS3Key());
        List<Document> extracted = new TikaDocumentReader(resource).get();
        List<Document> chunks = TokenTextSplitter.builder().withChunkSize(CHUNK_SIZE_TOKENS).build()
                .apply(extracted);

        List<Document> tagged = chunks.stream()
                .map(chunk -> tagWithSourceMetadata(chunk, source))
                .toList();

        vectorStore.add(tagged);
        source.setStatus(SourceStatus.READY);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void markFailed(UUID sourceId, String errorMessage) {
        sourceRepository.findById(sourceId).ifPresent(source -> {
            source.setStatus(SourceStatus.FAILED);
            source.setErrorMessage(errorMessage);
        });
    }

    private Document tagWithSourceMetadata(Document chunk, Source source) {
        // "chunk_index" ja vem preenchido pelo TokenTextSplitter (TextSplitter base class).
        return chunk.mutate()
                .metadata("source_id", source.getId().toString())
                .metadata("embedding_model", embeddingModel)
                .build();
    }
}
