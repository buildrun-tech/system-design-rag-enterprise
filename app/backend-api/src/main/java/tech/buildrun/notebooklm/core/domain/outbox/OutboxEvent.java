package tech.buildrun.notebooklm.core.domain.outbox;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public class OutboxEvent {

    private final UUID id;
    private final OutboxEventType type;
    private final UUID aggregateId;
    private final Map<String, String> payload;
    private OutboxStatus status;
    private int attempts;
    private Instant nextAttemptAt;
    private String lastError;
    private Instant sentAt;

    private OutboxEvent(UUID id, OutboxEventType type, UUID aggregateId, Map<String, String> payload,
                        OutboxStatus status, int attempts, Instant nextAttemptAt, String lastError, Instant sentAt) {
        this.id = id;
        this.type = type;
        this.aggregateId = aggregateId;
        this.payload = payload;
        this.status = status;
        this.attempts = attempts;
        this.nextAttemptAt = nextAttemptAt;
        this.lastError = lastError;
        this.sentAt = sentAt;
    }

    public static OutboxEvent create(OutboxEventType type, UUID aggregateId, Map<String, String> payload, Instant now) {
        return new OutboxEvent(null, type, aggregateId, Map.copyOf(payload), OutboxStatus.PENDING, 0, now, null, null);
    }

    /** Reconstrução a partir da persistência; uso exclusivo dos adapters de saída. */
    public static OutboxEvent restore(UUID id, OutboxEventType type, UUID aggregateId, Map<String, String> payload,
                                      OutboxStatus status, int attempts, Instant nextAttemptAt, String lastError,
                                      Instant sentAt) {
        return new OutboxEvent(id, type, aggregateId, payload, status, attempts, nextAttemptAt, lastError, sentAt);
    }

    public void markSent(Instant now) {
        this.status = OutboxStatus.SENT;
        this.sentAt = now;
        this.lastError = null;
    }

    public void markRetry(String error, Instant now, Duration backoff) {
        this.attempts++;
        this.lastError = error;
        this.nextAttemptAt = now.plus(backoff);
    }

    public void markFailed(String error) {
        this.attempts++;
        this.status = OutboxStatus.FAILED;
        this.lastError = error;
    }

    public UUID id() { return id; }
    public OutboxEventType type() { return type; }
    public UUID aggregateId() { return aggregateId; }
    public Map<String, String> payload() { return payload; }
    public OutboxStatus status() { return status; }
    public int attempts() { return attempts; }
    public Instant nextAttemptAt() { return nextAttemptAt; }
    public String lastError() { return lastError; }
    public Instant sentAt() { return sentAt; }
}
