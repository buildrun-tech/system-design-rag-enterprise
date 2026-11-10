package tech.buildrun.notebooklm.adapter.out.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import tech.buildrun.notebooklm.core.domain.source.SourceStatus;
import tech.buildrun.notebooklm.core.domain.source.SourceType;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "tb_sources")
public class SourceJpaEntity {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "notebook_id", nullable = false)
    private UUID notebookId;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SourceType type;

    @Column(name = "s3_key")
    private String s3Key;

    @Column
    private String url;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SourceStatus status = SourceStatus.PENDING;

    @Column(name = "error_message")
    private String errorMessage;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected SourceJpaEntity() {
    }

    public SourceJpaEntity(UUID id, UUID notebookId, String name, SourceType type, String s3Key, String url,
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

    public UUID getId() { return id; }
    public UUID getNotebookId() { return notebookId; }
    public String getName() { return name; }
    public SourceType getType() { return type; }
    public String getS3Key() { return s3Key; }
    public String getUrl() { return url; }
    public SourceStatus getStatus() { return status; }
    public String getErrorMessage() { return errorMessage; }
    public Instant getCreatedAt() { return createdAt; }
}
