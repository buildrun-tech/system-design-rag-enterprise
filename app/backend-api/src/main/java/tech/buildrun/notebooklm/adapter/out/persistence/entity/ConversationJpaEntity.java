package tech.buildrun.notebooklm.adapter.out.persistence.entity;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "tb_conversations")
public class ConversationJpaEntity {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "notebook_id", nullable = false)
    private UUID notebookId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "tb_conversation_active_sources", joinColumns = @JoinColumn(name = "conversation_id"))
    @Column(name = "source_id")
    private Set<UUID> activeSourceIds = new HashSet<>();

    protected ConversationJpaEntity() {
    }

    public ConversationJpaEntity(UUID id, UUID notebookId, Set<UUID> activeSourceIds, Instant createdAt) {
        this.id = id;
        this.notebookId = notebookId;
        this.activeSourceIds = new HashSet<>(activeSourceIds);
        this.createdAt = createdAt;
    }

    public UUID getId() { return id; }
    public UUID getNotebookId() { return notebookId; }
    public Set<UUID> getActiveSourceIds() { return activeSourceIds; }
    public Instant getCreatedAt() { return createdAt; }
}
