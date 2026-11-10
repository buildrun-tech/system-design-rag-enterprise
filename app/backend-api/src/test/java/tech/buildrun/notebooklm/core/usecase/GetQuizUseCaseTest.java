package tech.buildrun.notebooklm.core.usecase;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tech.buildrun.notebooklm.core.domain.conversation.Conversation;
import tech.buildrun.notebooklm.core.domain.exception.ConversationNotFoundException;
import tech.buildrun.notebooklm.core.domain.exception.QuizNotFoundException;
import tech.buildrun.notebooklm.core.domain.quiz.Quiz;
import tech.buildrun.notebooklm.core.domain.quiz.QuizStatus;
import tech.buildrun.notebooklm.core.port.out.persistence.ConversationRepositoryPort;
import tech.buildrun.notebooklm.core.port.out.persistence.QuizRepositoryPort;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetQuizUseCaseTest {

    @Mock
    private ConversationRepositoryPort conversationRepository;
    @Mock
    private QuizRepositoryPort quizRepository;

    private final UUID ownerId = UUID.randomUUID();
    private final UUID conversationId = UUID.randomUUID();
    private final UUID quizId = UUID.randomUUID();

    private GetQuizUseCase useCase() {
        return new GetQuizUseCase(conversationRepository, quizRepository);
    }

    private void ownConversation() {
        when(conversationRepository.findByIdAndOwnerId(conversationId, ownerId)).thenReturn(Optional.of(
                Conversation.restore(conversationId, UUID.randomUUID(), Set.of(), null)));
    }

    private Quiz processingQuiz(Instant updatedAt) {
        return Quiz.restore(quizId, conversationId, QuizStatus.PROCESSING, null, null, updatedAt, updatedAt);
    }

    @Test
    void throwsWhenConversationNotOwned() {
        when(conversationRepository.findByIdAndOwnerId(conversationId, ownerId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase().get(conversationId, quizId, ownerId))
                .isInstanceOf(ConversationNotFoundException.class);
    }

    @Test
    void throwsWhenQuizBelongsToAnotherConversation() {
        ownConversation();
        when(quizRepository.findByIdAndConversationId(quizId, conversationId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase().get(conversationId, quizId, ownerId))
                .isInstanceOf(QuizNotFoundException.class);
    }

    @Test
    void returnsFailedForOrphanedInFlightQuiz() {
        ownConversation();
        when(quizRepository.findByIdAndConversationId(quizId, conversationId))
                .thenReturn(Optional.of(processingQuiz(Instant.now().minus(11, ChronoUnit.MINUTES))));

        Quiz quiz = useCase().get(conversationId, quizId, ownerId);

        assertThat(quiz.status()).isEqualTo(QuizStatus.FAILED);
        assertThat(quiz.errorMessage()).isEqualTo("Tempo excedido ao gerar o quiz");
        assertThat(quiz.result()).isNull();
    }

    @Test
    void returnsCurrentStatusForFreshQuiz() {
        ownConversation();
        when(quizRepository.findByIdAndConversationId(quizId, conversationId))
                .thenReturn(Optional.of(processingQuiz(Instant.now())));

        assertThat(useCase().get(conversationId, quizId, ownerId).status()).isEqualTo(QuizStatus.PROCESSING);
    }
}
