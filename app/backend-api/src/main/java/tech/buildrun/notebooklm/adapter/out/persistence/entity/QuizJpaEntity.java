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
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;
import tech.buildrun.notebooklm.core.domain.quiz.QuizStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "tb_quizzes")
public class QuizJpaEntity {

    public record QuestionJson(String question, List<String> options, int correctIndex, String explanation) {
    }

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "conversation_id", nullable = false)
    private UUID conversationId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private QuizStatus status = QuizStatus.PENDING;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<QuestionJson> result;

    @Column(name = "error_message")
    private String errorMessage;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected QuizJpaEntity() {
    }

    public QuizJpaEntity(UUID conversationId) {
        this.conversationId = conversationId;
    }

    public void apply(QuizStatus status, List<QuestionJson> result, String errorMessage) {
        this.status = status;
        this.result = result;
        this.errorMessage = errorMessage;
    }

    public UUID getId() { return id; }
    public UUID getConversationId() { return conversationId; }
    public QuizStatus getStatus() { return status; }
    public List<QuestionJson> getResult() { return result; }
    public String getErrorMessage() { return errorMessage; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
