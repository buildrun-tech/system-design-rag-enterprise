package tech.buildrun.notebooklm.adapter.out.persistence;

import org.springframework.stereotype.Component;
import tech.buildrun.notebooklm.adapter.out.persistence.entity.QuizJpaEntity;
import tech.buildrun.notebooklm.adapter.out.persistence.mapper.QuizMapper;
import tech.buildrun.notebooklm.adapter.out.persistence.repository.QuizJpaRepository;
import tech.buildrun.notebooklm.core.domain.quiz.Quiz;
import tech.buildrun.notebooklm.core.port.out.persistence.QuizRepositoryPort;

import java.util.Optional;
import java.util.UUID;

@Component
public class QuizRepositoryAdapter implements QuizRepositoryPort {

    private final QuizJpaRepository repository;

    public QuizRepositoryAdapter(QuizJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<Quiz> findById(UUID id) {
        return repository.findById(id).map(QuizMapper::toDomain);
    }

    @Override
    public Optional<Quiz> findByIdAndConversationId(UUID id, UUID conversationId) {
        return repository.findByIdAndConversationId(id, conversationId).map(QuizMapper::toDomain);
    }

    @Override
    public Quiz save(Quiz quiz) {
        QuizJpaEntity entity = quiz.id() == null
                ? QuizMapper.toEntity(quiz)
                : repository.findById(quiz.id()).orElseGet(() -> QuizMapper.toEntity(quiz));
        QuizMapper.copyState(quiz, entity);
        return QuizMapper.toDomain(repository.saveAndFlush(entity));
    }
}
