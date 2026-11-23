package tech.buildrun.notebooklm.adapter.out.queue;

import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import tech.buildrun.notebooklm.core.domain.outbox.OutboxEvent;
import tech.buildrun.notebooklm.core.domain.outbox.OutboxEventType;
import tech.buildrun.notebooklm.core.domain.source.IngestionRequest;
import tech.buildrun.notebooklm.core.port.out.IngestionQueuePort;
import tech.buildrun.notebooklm.core.port.out.persistence.OutboxRepositoryPort;

import java.time.Clock;
import java.util.HashMap;
import java.util.Map;

/** Grava o pedido de ingestão no outbox, na transação do chamador; o envio ao SQS é feito pelo relay. */
@Component
public class SqsIngestionQueueAdapter implements IngestionQueuePort {

    private final OutboxRepositoryPort outbox;
    private final Clock clock;

    public SqsIngestionQueueAdapter(OutboxRepositoryPort outbox, Clock clock) {
        this.outbox = outbox;
        this.clock = clock;
    }

    @Override
    public void enqueue(IngestionRequest request) {
        Map<String, String> payload = new HashMap<>();
        payload.put("sourceId", request.sourceId().toString());
        payload.put("userId", request.userId().toString());
        String requestId = MDC.get("requestId");
        if (requestId != null) payload.put("requestId", requestId);
        outbox.save(OutboxEvent.create(OutboxEventType.INGESTION_REQUESTED, request.sourceId(), payload,
                clock.instant()));
    }
}
