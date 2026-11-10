package tech.buildrun.notebooklm.core.port.out.persistence;

import tech.buildrun.notebooklm.core.domain.quiz.Quiz;

import java.util.Optional;
import java.util.UUID;

public interface QuizRepositoryPort {

    Optional<Quiz> findById(UUID id);

    Optional<Quiz> findByIdAndConversationId(UUID id, UUID conversationId);

    Quiz save(Quiz quiz);
}
