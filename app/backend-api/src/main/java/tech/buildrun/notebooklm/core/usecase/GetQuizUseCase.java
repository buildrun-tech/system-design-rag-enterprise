package tech.buildrun.notebooklm.core.usecase;

import tech.buildrun.notebooklm.core.domain.exception.ConversationNotFoundException;
import tech.buildrun.notebooklm.core.domain.exception.QuizNotFoundException;
import tech.buildrun.notebooklm.core.domain.quiz.Quiz;
import tech.buildrun.notebooklm.core.port.in.GetQuizPort;
import tech.buildrun.notebooklm.core.port.out.persistence.ConversationRepositoryPort;
import tech.buildrun.notebooklm.core.port.out.persistence.QuizRepositoryPort;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

public class GetQuizUseCase implements GetQuizPort {

    static final Duration STALE_AFTER = Duration.ofMinutes(10);

    private final ConversationRepositoryPort conversationRepository;
    private final QuizRepositoryPort quizRepository;

    public GetQuizUseCase(ConversationRepositoryPort conversationRepository, QuizRepositoryPort quizRepository) {
        this.conversationRepository = conversationRepository;
        this.quizRepository = quizRepository;
    }

    @Override
    public Quiz get(UUID conversationId, UUID quizId, UUID ownerId) {
        conversationRepository.findByIdAndOwnerId(conversationId, ownerId)
                .orElseThrow(ConversationNotFoundException::new);
        return quizRepository.findByIdAndConversationId(quizId, conversationId)
                .orElseThrow(QuizNotFoundException::new)
                .expiredIfStale(Instant.now(), STALE_AFTER);
    }
}
