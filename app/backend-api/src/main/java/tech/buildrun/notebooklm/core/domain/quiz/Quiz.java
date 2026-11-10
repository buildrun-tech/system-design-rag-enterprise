package tech.buildrun.notebooklm.core.domain.quiz;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public class Quiz {

    private static final String STALE_MESSAGE = "Tempo excedido ao gerar o quiz";

    private final UUID id;
    private final UUID conversationId;
    private QuizStatus status;
    private List<QuizQuestion> result;
    private String errorMessage;
    private final Instant createdAt;
    private final Instant updatedAt;

    private Quiz(UUID id, UUID conversationId, QuizStatus status, List<QuizQuestion> result, String errorMessage,
                 Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.conversationId = conversationId;
        this.status = status;
        this.result = result;
        this.errorMessage = errorMessage;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static Quiz create(UUID conversationId) {
        return new Quiz(null, conversationId, QuizStatus.PENDING, null, null, null, null);
    }

    /** Reconstrução a partir da persistência; uso exclusivo dos adapters de saída. */
    public static Quiz restore(UUID id, UUID conversationId, QuizStatus status, List<QuizQuestion> result,
                               String errorMessage, Instant createdAt, Instant updatedAt) {
        return new Quiz(id, conversationId, status, result, errorMessage, createdAt, updatedAt);
    }

    public void markProcessing() {
        this.status = QuizStatus.PROCESSING;
    }

    public void complete(List<QuizQuestion> questions) {
        this.status = QuizStatus.COMPLETED;
        this.result = questions;
    }

    public void fail(String message) {
        this.status = QuizStatus.FAILED;
        this.errorMessage = message;
    }

    public Quiz expiredIfStale(Instant now, Duration staleAfter) {
        boolean inFlight = status == QuizStatus.PENDING || status == QuizStatus.PROCESSING;
        if (inFlight && updatedAt.isBefore(now.minus(staleAfter))) {
            return new Quiz(id, conversationId, QuizStatus.FAILED, null, STALE_MESSAGE, createdAt, updatedAt);
        }
        return this;
    }

    public UUID id() { return id; }
    public UUID conversationId() { return conversationId; }
    public QuizStatus status() { return status; }
    public List<QuizQuestion> result() { return result; }
    public String errorMessage() { return errorMessage; }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }
}
