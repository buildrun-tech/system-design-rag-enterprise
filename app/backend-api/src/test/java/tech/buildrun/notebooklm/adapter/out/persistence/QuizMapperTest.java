package tech.buildrun.notebooklm.adapter.out.persistence;

import org.junit.jupiter.api.Test;
import tech.buildrun.notebooklm.adapter.out.persistence.entity.QuizJpaEntity;
import tech.buildrun.notebooklm.adapter.out.persistence.mapper.QuizMapper;
import tech.buildrun.notebooklm.core.domain.quiz.Quiz;
import tech.buildrun.notebooklm.core.domain.quiz.QuizQuestion;
import tech.buildrun.notebooklm.core.domain.quiz.QuizStatus;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class QuizMapperTest {

    @Test
    void roundTripPreservesStateAndQuestions() {
        UUID conversationId = UUID.randomUUID();
        Quiz quiz = Quiz.create(conversationId);
        quiz.complete(List.of(new QuizQuestion("Q", List.of("a", "b", "c", "d"), 2, "e")));

        Quiz back = QuizMapper.toDomain(QuizMapper.toEntity(quiz));

        assertThat(back.conversationId()).isEqualTo(conversationId);
        assertThat(back.status()).isEqualTo(QuizStatus.COMPLETED);
        assertThat(back.result()).isEqualTo(quiz.result());
    }

    @Test
    void copyStateUpdatesExistingEntityWithFailure() {
        QuizJpaEntity entity = new QuizJpaEntity(UUID.randomUUID());
        Quiz quiz = Quiz.create(entity.getConversationId());
        quiz.fail("boom");

        QuizMapper.copyState(quiz, entity);

        assertThat(entity.getStatus()).isEqualTo(QuizStatus.FAILED);
        assertThat(entity.getErrorMessage()).isEqualTo("boom");
        assertThat(entity.getResult()).isNull();
    }
}
