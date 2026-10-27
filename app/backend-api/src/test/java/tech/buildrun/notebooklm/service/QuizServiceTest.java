package tech.buildrun.notebooklm.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import tech.buildrun.notebooklm.dto.QuizCreatedResponse;
import tech.buildrun.notebooklm.dto.QuizResponse;
import tech.buildrun.notebooklm.entity.Conversation;
import tech.buildrun.notebooklm.entity.Notebook;
import tech.buildrun.notebooklm.entity.Quiz;
import tech.buildrun.notebooklm.entity.QuizStatus;
import tech.buildrun.notebooklm.entity.Source;
import tech.buildrun.notebooklm.entity.SourceType;
import tech.buildrun.notebooklm.entity.User;
import tech.buildrun.notebooklm.exception.ConversationNotFoundException;
import tech.buildrun.notebooklm.exception.QuizNotFoundException;
import tech.buildrun.notebooklm.repository.ConversationRepository;
import tech.buildrun.notebooklm.repository.QuizRepository;
import tech.buildrun.notebooklm.repository.SourceRepository;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QuizServiceTest {

    @Mock
    private ConversationRepository conversationRepository;
    @Mock
    private SourceRepository sourceRepository;
    @Mock
    private QuizRepository quizRepository;
    @Mock
    private QuizGenerator quizGenerator;

    private final UUID ownerId = UUID.randomUUID();
    private final UUID conversationId = UUID.randomUUID();

    // executor síncrono: o job roda na própria chamada
    private QuizService newService() {
        return new QuizService(conversationRepository, sourceRepository, quizRepository, quizGenerator, Runnable::run);
    }

    private Conversation conversationWithSource(UUID sourceId) {
        Notebook notebook = new Notebook(new User("sub", "o@test.com", "O"), "N", null);
        Conversation conversation = new Conversation(notebook);
        Source source = new Source(notebook, "doc.md", SourceType.FILE, "k", null);
        ReflectionTestUtils.setField(source, "id", sourceId);
        conversation.getActiveSources().add(source);
        return conversation;
    }

    @Test
    void createPersistsPendingQuizAndStartsGenerationWithActiveSources() {
        UUID sourceId = UUID.randomUUID();
        UUID quizId = UUID.randomUUID();
        when(conversationRepository.findByIdAndNotebookOwnerId(conversationId, ownerId))
                .thenReturn(Optional.of(conversationWithSource(sourceId)));
        when(quizRepository.save(any(Quiz.class))).thenAnswer(invocation -> {
            Quiz quiz = invocation.getArgument(0);
            ReflectionTestUtils.setField(quiz, "id", quizId);
            return quiz;
        });

        QuizCreatedResponse response = newService().create(conversationId, ownerId);

        assertThat(response.quizId()).isEqualTo(quizId);
        assertThat(response.status()).isEqualTo(QuizStatus.PENDING);
        verify(quizGenerator).generate(quizId, List.of(sourceId));
    }

    @Test
    void createThrowsWhenConversationNotOwned() {
        when(conversationRepository.findByIdAndNotebookOwnerId(conversationId, ownerId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> newService().create(conversationId, ownerId))
                .isInstanceOf(ConversationNotFoundException.class);
        verify(quizRepository, never()).save(any());
    }

    @Test
    void getThrowsWhenQuizBelongsToAnotherConversation() {
        UUID quizId = UUID.randomUUID();
        when(conversationRepository.findByIdAndNotebookOwnerId(conversationId, ownerId))
                .thenReturn(Optional.of(conversationWithSource(UUID.randomUUID())));
        when(quizRepository.findByIdAndConversationId(quizId, conversationId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> newService().get(conversationId, quizId, ownerId))
                .isInstanceOf(QuizNotFoundException.class);
    }

    @Test
    void getReturnsFailedForOrphanedInFlightQuiz() {
        UUID quizId = UUID.randomUUID();
        Quiz quiz = new Quiz(conversationWithSource(UUID.randomUUID()));
        quiz.markProcessing();
        ReflectionTestUtils.setField(quiz, "id", quizId);
        ReflectionTestUtils.setField(quiz, "updatedAt", Instant.now().minus(11, ChronoUnit.MINUTES));
        when(conversationRepository.findByIdAndNotebookOwnerId(conversationId, ownerId))
                .thenReturn(Optional.of(conversationWithSource(UUID.randomUUID())));
        when(quizRepository.findByIdAndConversationId(quizId, conversationId)).thenReturn(Optional.of(quiz));

        QuizResponse response = newService().get(conversationId, quizId, ownerId);

        assertThat(response.status()).isEqualTo(QuizStatus.FAILED);
        assertThat(response.errorMessage()).isNotBlank();
    }

    @Test
    void getReturnsCurrentStatusForFreshQuiz() {
        UUID quizId = UUID.randomUUID();
        Quiz quiz = new Quiz(conversationWithSource(UUID.randomUUID()));
        quiz.markProcessing();
        ReflectionTestUtils.setField(quiz, "id", quizId);
        ReflectionTestUtils.setField(quiz, "updatedAt", Instant.now());
        when(conversationRepository.findByIdAndNotebookOwnerId(conversationId, ownerId))
                .thenReturn(Optional.of(conversationWithSource(UUID.randomUUID())));
        when(quizRepository.findByIdAndConversationId(quizId, conversationId)).thenReturn(Optional.of(quiz));

        assertThat(newService().get(conversationId, quizId, ownerId).status()).isEqualTo(QuizStatus.PROCESSING);
    }
}
