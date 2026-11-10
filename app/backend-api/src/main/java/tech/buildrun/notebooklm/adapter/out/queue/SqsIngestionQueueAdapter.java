package tech.buildrun.notebooklm.adapter.out.queue;

import io.awspring.cloud.sqs.operations.SqsTemplate;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tech.buildrun.notebooklm.core.domain.source.IngestionRequest;
import tech.buildrun.notebooklm.core.port.out.IngestionQueuePort;

@Component
public class SqsIngestionQueueAdapter implements IngestionQueuePort {

    private final SqsTemplate sqsTemplate;
    private final String ingestionQueueUrl;

    public SqsIngestionQueueAdapter(SqsTemplate sqsTemplate,
                                    @Value("${aws.sqs.ingestion-queue-url}") String ingestionQueueUrl) {
        this.sqsTemplate = sqsTemplate;
        this.ingestionQueueUrl = ingestionQueueUrl;
    }

    @Override
    public void enqueue(IngestionRequest request) {
        sqsTemplate.send(ingestionQueueUrl,
                new IngestionMessage(request.sourceId(), MDC.get("requestId"), request.userId()));
    }
}
