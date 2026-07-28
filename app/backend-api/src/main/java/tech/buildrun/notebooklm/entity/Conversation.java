package tech.buildrun.notebooklm.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "tb_conversations")
public class Conversation {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "notebook_id", nullable = false)
    private Notebook notebook;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @ManyToMany
    @JoinTable(
            name = "tb_conversation_active_sources",
            joinColumns = @JoinColumn(name = "conversation_id"),
            inverseJoinColumns = @JoinColumn(name = "source_id")
    )
    private Set<Source> activeSources = new HashSet<>();

    protected Conversation() {
    }

    public Conversation(Notebook notebook) {
        this.notebook = notebook;
    }

    public UUID getId() {
        return id;
    }

    public Notebook getNotebook() {
        return notebook;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Set<Source> getActiveSources() {
        return activeSources;
    }
}
