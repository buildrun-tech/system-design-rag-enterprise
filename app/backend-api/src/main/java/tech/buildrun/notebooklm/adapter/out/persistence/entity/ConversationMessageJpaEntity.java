package tech.buildrun.notebooklm.adapter.out.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import tech.buildrun.notebooklm.core.domain.conversation.CitedSource;
import tech.buildrun.notebooklm.core.domain.conversation.MessageRole;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "tb_conversation_messages")
public class ConversationMessageJpaEntity {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "conversation_id", nullable = false)
    private UUID conversationId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MessageRole role;

    @Column(nullable = false)
    private String content;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private List<CitedSource> sources = List.of();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ConversationMessageJpaEntity() {
    }

    public ConversationMessageJpaEntity(UUID id, UUID conversationId, MessageRole role, String content,
                                        List<CitedSource> sources, Instant createdAt) {
        this.id = id;
        this.conversationId = conversationId;
        this.role = role;
        this.content = content;
        this.sources = sources;
        this.createdAt = createdAt;
    }

    public UUID getId() { return id; }
    public UUID getConversationId() { return conversationId; }
    public MessageRole getRole() { return role; }
    public String getContent() { return content; }
    public List<CitedSource> getSources() { return sources; }
    public Instant getCreatedAt() { return createdAt; }
}
