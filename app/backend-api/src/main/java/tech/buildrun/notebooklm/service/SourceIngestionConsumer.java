package tech.buildrun.notebooklm.service;

import io.awspring.cloud.s3.S3Template;
import com.openai.errors.OpenAIServiceException;
import io.awspring.cloud.sqs.annotation.SqsListener;
import io.awspring.cloud.sqs.listener.Visibility;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
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
import java.util.Set;
import java.util.UUID;

// ponytail: sem essa guarda, contexto Spring que sobe sem fila configurada
// (testes, dev sem LocalStack) quebra no boot inteiro por causa do listener.
@Service
@ConditionalOnExpression("T(org.springframework.util.StringUtils).hasText('${aws.sqs.ingestion-queue-url:}')")
public class SourceIngestionConsumer {

    private static final Logger log = LoggerFactory.getLogger(SourceIngestionConsumer.class);

    // ponytail: chunk sem overlap — TokenTextSplitter (Spring AI) nao suporta overlap.
    // Upgrade: splitter custom se overlap entre chunks virar necessidade real.
    private static final int CHUNK_SIZE_TOKENS = 512;

    // Cada chunk enriquecido renova o visibility timeout por este tempo: a mensagem fica invisível enquanto o
    // consumer trabalha, e some do "in flight" rápido se ele morrer.
    private static final int VISIBILITY_EXTENSION_SECONDS = 180;

    // Erros do provider que repetir não resolve (crédito/limite, auth, request inválido): ack em vez de redelivery.
    private static final Set<Integer> PERMANENT_PROVIDER_STATUS = Set.of(400, 401, 402, 403, 404, 422);

    private final SourceRepository sourceRepository;
    private final S3Template s3Template;
    private final VectorStore vectorStore;
    private final ChunkEnrichmentService chunkEnrichmentService;
    private final String bucketName;
    private final String embeddingModel;

    public SourceIngestionConsumer(SourceRepository sourceRepository, S3Template s3Template, VectorStore vectorStore,
                                    ChunkEnrichmentService chunkEnrichmentService,
                                    @Value("${aws.s3.bucket-name}") String bucketName,
                                    @Value("${spring.ai.openai.embedding.options.model}") String embeddingModel) {
        this.sourceRepository = sourceRepository;
        this.s3Template = s3Template;
        this.vectorStore = vectorStore;
        this.chunkEnrichmentService = chunkEnrichmentService;
        this.bucketName = bucketName;
        this.embeddingModel = embeddingModel;
    }

    @Transactional
    @SqsListener("${aws.sqs.ingestion-queue-url}")
    public void process(IngestionMessage message, Visibility visibility) {
        MDC.put("requestId", message.requestId() != null ? message.requestId() : UUID.randomUUID().toString());
        if (message.userId() != null) {
            MDC.put("userId", message.userId().toString());
        }
        long start = System.nanoTime();
        log.info("Consumer in: SourceIngestionConsumer.process message={}", message);
        try {
            ingest(message.sourceId(), visibility);
            log.info("Consumer out: SourceIngestionConsumer.process sourceId={} status={} durationMs={}",
                    message.sourceId(), SourceStatus.READY, (System.nanoTime() - start) / 1_000_000);
        } catch (Exception e) {
            // logger.error com throwable imprime a cadeia completa de causas (root cause incluida)
            log.error("Source ingestion failed: sourceId={}", message.sourceId(), e);
            // marca FAILED em transacao propria: a transacao de ingest() ja fez
            // rollback, entao o status so fica visivel se persistido a parte.
            markFailed(message.sourceId(), e.getMessage());
            if (isPermanentProviderError(e)) {
                // sem relançar: a mensagem é ack'ada e a source fica FAILED, em vez de ficar reentregando
                log.warn("Non-retryable provider error, message acknowledged: sourceId={}", message.sourceId());
                return;
            }
            throw e;
        } finally {
            MDC.clear();
        }
    }

    void ingest(UUID sourceId, Visibility visibility) {
        try {
            Source source = sourceRepository.findById(sourceId).orElseThrow(SourceNotFoundException::new);
            if (source.getStatus() == SourceStatus.READY) {
                // redelivery de mensagem já processada (ack perdido): reprocessar duplicaria os chunks
                log.info("Source already READY, skipping: sourceId={}", sourceId);
                return;
            }
            source.setStatus(SourceStatus.PROCESSING);

            var resource = s3Template.download(bucketName, source.getS3Key());
            List<Document> extracted = new TikaDocumentReader(resource).get();
            List<Document> chunks = TokenTextSplitter.builder().withChunkSize(CHUNK_SIZE_TOKENS).build()
                    .apply(extracted);

            // enrich obrigatório: falha aqui propaga e a source vira FAILED
            List<Document> tagged = chunkEnrichmentService.enrich(chunks, () -> extendVisibility(visibility)).stream()
                    .map(chunk -> tagWithSourceMetadata(chunk, source))
                    .toList();

            log.info("Source ingestion: sourceId={} s3Key={} extractedDocs={} chunks={}",
                    sourceId, source.getS3Key(), extracted.size(), tagged.size());
            // idempotente: remove chunks de execuções anteriores desta source antes de gravar
            vectorStore.delete("source_id == '%s'".formatted(sourceId));
            vectorStore.add(tagged);
            source.setStatus(SourceStatus.READY);
        } catch (SourceNotFoundException e) {
            throw e;
        } catch (Exception e) {
            log.error("Transactional Source ingestion failed: sourceId={}", sourceId, e);
            throw new RuntimeException(e);
        }
    }

    void markFailed(UUID sourceId, String errorMessage) {
        // READY nunca regride: uma entrega duplicada que falha não pode invalidar uma ingestão já concluída
        sourceRepository.findById(sourceId).filter(source -> source.getStatus() != SourceStatus.READY).ifPresent(source -> {
            source.setStatus(SourceStatus.FAILED);
            source.setErrorMessage(errorMessage);
        });
    }

    private void extendVisibility(Visibility visibility) {
        if (visibility == null) return;
        try {
            visibility.changeTo(VISIBILITY_EXTENSION_SECONDS);
        } catch (RuntimeException e) {
            log.warn("Could not extend SQS visibility timeout", e);
        }
    }

    static boolean isPermanentProviderError(Throwable error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof OpenAIServiceException provider
                    && PERMANENT_PROVIDER_STATUS.contains(provider.statusCode())) {
                return true;
            }
        }
        return false;
    }

    private Document tagWithSourceMetadata(Document chunk, Source source) {
        // "chunk_index" ja vem preenchido pelo TokenTextSplitter (TextSplitter base class).
        return chunk.mutate()
                .metadata("source_id", source.getId().toString())
                .metadata("embedding_model", embeddingModel)
                .build();
    }
}
