package tech.buildrun.notebooklm.adapter.in.messaging;

import com.openai.errors.OpenAIServiceException;
import io.awspring.cloud.sqs.annotation.SqsListener;
import io.awspring.cloud.sqs.listener.Visibility;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;
import tech.buildrun.notebooklm.core.port.in.IngestSourcePort;

import java.util.Set;
import java.util.UUID;

/**
 * Sem relançar a exceção, a mensagem SQS é ack'ada. Falhas permanentes do provider (400, 401, 402, 403, 404, 422)
 * são ack'adas com a source em FAILED; qualquer outra falha é relançada para a mensagem ser reentregue. Cada chunk
 * enriquecido renova o visibility timeout. O listener só existe se a fila de ingestão estiver configurada.
 */
@Component
@ConditionalOnExpression("T(org.springframework.util.StringUtils).hasText('${aws.sqs.ingestion-queue-url:}')")
public class SqsIngestionListener {

    private static final Logger log = LoggerFactory.getLogger(SqsIngestionListener.class);

    private static final int VISIBILITY_EXTENSION_SECONDS = 180;

    private static final Set<Integer> PERMANENT_PROVIDER_STATUS = Set.of(400, 401, 402, 403, 404, 422);

    private final IngestSourcePort ingestSource;

    public SqsIngestionListener(IngestSourcePort ingestSource) {
        this.ingestSource = ingestSource;
    }

    @SqsListener("${aws.sqs.ingestion-queue-url}")
    public void process(IngestionPayload message, Visibility visibility) {
        MDC.put("requestId", message.requestId() != null ? message.requestId() : UUID.randomUUID().toString());
        if (message.userId() != null) {
            MDC.put("userId", message.userId().toString());
        }
        long start = System.nanoTime();
        log.info("Consumer in: SqsIngestionListener.process message={}", message);
        try {
            ingestSource.ingest(message.sourceId(), () -> extendVisibility(visibility));
            log.info("Consumer out: SqsIngestionListener.process sourceId={} durationMs={}",
                    message.sourceId(), (System.nanoTime() - start) / 1_000_000);
        } catch (Exception e) {
            log.error("Source ingestion failed: sourceId={}", message.sourceId(), e);
            if (isPermanentProviderError(e)) {
                log.warn("Non-retryable provider error, message acknowledged: sourceId={}", message.sourceId());
                return;
            }
            throw e;
        } finally {
            MDC.clear();
        }
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
}
