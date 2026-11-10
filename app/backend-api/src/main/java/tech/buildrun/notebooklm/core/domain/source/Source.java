package tech.buildrun.notebooklm.core.domain.source;

import tech.buildrun.notebooklm.core.domain.exception.InvalidStateTransitionException;

import java.time.Instant;
import java.util.UUID;

public class Source {

    private final UUID id;
    private final UUID notebookId;
    private final String name;
    private final SourceType type;
    private String s3Key;
    private final String url;
    private SourceStatus status;
    private String errorMessage;
    private final Instant createdAt;

    private Source(UUID id, UUID notebookId, String name, SourceType type, String s3Key, String url,
                   SourceStatus status, String errorMessage, Instant createdAt) {
        this.id = id;
        this.notebookId = notebookId;
        this.name = name;
        this.type = type;
        this.s3Key = s3Key;
        this.url = url;
        this.status = status;
        this.errorMessage = errorMessage;
        this.createdAt = createdAt;
    }

    public static Source create(UUID notebookId, String name, SourceType type, String s3Key, String url) {
        return new Source(null, notebookId, name, type, s3Key, url, SourceStatus.PENDING, null, null);
    }

    /** Reconstrução a partir da persistência; uso exclusivo dos adapters de saída. */
    public static Source restore(UUID id, UUID notebookId, String name, SourceType type, String s3Key, String url,
                                 SourceStatus status, String errorMessage, Instant createdAt) {
        return new Source(id, notebookId, name, type, s3Key, url, status, errorMessage, createdAt);
    }

    public void storedAt(String s3Key) {
        this.s3Key = s3Key;
    }

    public void markProcessing() {
        if (status == SourceStatus.READY) {
            throw new InvalidStateTransitionException("Source", status, "markProcessing");
        }
        this.status = SourceStatus.PROCESSING;
        this.errorMessage = null;
    }

    public void markReady() {
        if (status != SourceStatus.PROCESSING) {
            throw new InvalidStateTransitionException("Source", status, "markReady");
        }
        this.status = SourceStatus.READY;
    }

    public void markFailed(String reason) {
        if (status == SourceStatus.READY) {
            return;
        }
        this.status = SourceStatus.FAILED;
        this.errorMessage = reason;
    }

    public boolean belongsTo(UUID notebookId) {
        return this.notebookId.equals(notebookId);
    }

    public boolean isReady() {
        return status == SourceStatus.READY;
    }

    public UUID id() { return id; }
    public UUID notebookId() { return notebookId; }
    public String name() { return name; }
    public SourceType type() { return type; }
    public String s3Key() { return s3Key; }
    public String url() { return url; }
    public SourceStatus status() { return status; }
    public String errorMessage() { return errorMessage; }
    public Instant createdAt() { return createdAt; }
}
