package tech.buildrun.notebooklm.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "tb_sources")
public class Source {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "notebook_id", nullable = false)
    private Notebook notebook;

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

    protected Source() {
    }

    public Source(Notebook notebook, String name, SourceType type, String s3Key, String url) {
        this.notebook = notebook;
        this.name = name;
        this.type = type;
        this.s3Key = s3Key;
        this.url = url;
    }

    public UUID getId() {
        return id;
    }

    public Notebook getNotebook() {
        return notebook;
    }

    public String getName() {
        return name;
    }

    public SourceType getType() {
        return type;
    }

    public String getS3Key() {
        return s3Key;
    }

    public String getUrl() {
        return url;
    }

    public SourceStatus getStatus() {
        return status;
    }

    public void setStatus(SourceStatus status) {
        this.status = status;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
