package tech.buildrun.notebooklm.adapter.in.scheduling;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import tech.buildrun.notebooklm.core.port.in.PurgeOutboxPort;
import tech.buildrun.notebooklm.core.port.in.RelayOutboxPort;

/** Só existe se a fila de ingestão estiver configurada; cada instância roda o seu poll, o SKIP LOCKED divide o lote. */
@Component
@ConditionalOnExpression("T(org.springframework.util.StringUtils).hasText('${aws.sqs.ingestion-queue-url:}')")
public class OutboxScheduler {

    private static final Logger log = LoggerFactory.getLogger(OutboxScheduler.class);

    private final RelayOutboxPort relay;
    private final PurgeOutboxPort purge;

    public OutboxScheduler(RelayOutboxPort relay, PurgeOutboxPort purge) {
        this.relay = relay;
        this.purge = purge;
    }

    @Scheduled(fixedDelayString = "${outbox.poll-interval:1s}")
    public void relay() {
        try {
            int sent = relay.relay();
            if (sent > 0) log.atInfo().setMessage("Outbox relay").addKeyValue("events", sent).log();
        } catch (RuntimeException e) {
            log.atError().setMessage("Outbox relay failed").setCause(e).log();
        }
    }

    @Scheduled(fixedDelayString = "${outbox.purge-interval:1h}", initialDelayString = "${outbox.purge-interval:1h}")
    public void purge() {
        try {
            int removed = purge.purge();
            if (removed > 0) log.atInfo().setMessage("Outbox purge").addKeyValue("removed", removed).log();
        } catch (RuntimeException e) {
            log.atError().setMessage("Outbox purge failed").setCause(e).log();
        }
    }
}
