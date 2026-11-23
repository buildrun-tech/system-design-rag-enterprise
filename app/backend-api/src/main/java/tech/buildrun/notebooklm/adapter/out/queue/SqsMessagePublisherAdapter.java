package tech.buildrun.notebooklm.adapter.out.queue;

import io.awspring.cloud.sqs.operations.SqsTemplate;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tech.buildrun.notebooklm.adapter.out.ExternalCallLog;
import tech.buildrun.notebooklm.core.domain.outbox.OutboxEvent;
import tech.buildrun.notebooklm.core.port.out.MessagePublisherPort;

import java.util.Map;
import java.util.UUID;

@Component
public class SqsMessagePublisherAdapter implements MessagePublisherPort {

    private final SqsTemplate sqsTemplate;
    private final String ingestionQueueUrl;

    public SqsMessagePublisherAdapter(SqsTemplate sqsTemplate,
                                      @Value("${aws.sqs.ingestion-queue-url}") String ingestionQueueUrl) {
        this.sqsTemplate = sqsTemplate;
        this.ingestionQueueUrl = ingestionQueueUrl;
    }

    @Override
    public void publish(OutboxEvent event) {
        Map<String, String> payload = event.payload();
        var message = new IngestionMessage(UUID.fromString(payload.get("sourceId")), payload.get("requestId"),
                UUID.fromString(payload.get("userId")));
        if (message.requestId() != null) MDC.put("requestId", message.requestId());
        try {
            ExternalCallLog.run("sqs", "send", () -> sqsTemplate.send(ingestionQueueUrl, message));
        } finally {
            MDC.remove("requestId");
        }
    }
}
