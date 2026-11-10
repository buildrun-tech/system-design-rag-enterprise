package tech.buildrun.notebooklm.adapter.in.web;

import org.junit.jupiter.api.Test;
import tech.buildrun.notebooklm.adapter.in.web.controller.QuizController;
import tech.buildrun.notebooklm.adapter.in.web.dto.QuizCreatedResponse;
import tech.buildrun.notebooklm.adapter.in.web.dto.QuizResponse;
import tech.buildrun.notebooklm.core.domain.quiz.Quiz;
import tech.buildrun.notebooklm.core.domain.quiz.QuizQuestion;
import tech.buildrun.notebooklm.core.domain.quiz.QuizStatus;
import tech.buildrun.notebooklm.core.domain.user.User;
import tech.buildrun.notebooklm.core.port.in.GenerateQuizPort;
import tech.buildrun.notebooklm.core.port.in.GetQuizPort;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class QuizControllerTest {

    @Test
    void createAndGetDelegateToPortsWithCurrentUserAndMapResponses() {
        GenerateQuizPort generate = mock(GenerateQuizPort.class);
        GetQuizPort get = mock(GetQuizPort.class);
        QuizController controller = new QuizController(generate, get);
        User user = User.restore(UUID.randomUUID(), "sub", "owner@test.com", "Owner", null);
        UUID conversationId = UUID.randomUUID();
        UUID quizId = UUID.randomUUID();
        Quiz pending = Quiz.restore(quizId, conversationId, QuizStatus.PENDING, null, null, null, null);
        Quiz completed = Quiz.restore(quizId, conversationId, QuizStatus.COMPLETED,
                List.of(new QuizQuestion("Q", List.of("a", "b", "c", "d"), 1, "e")), null, null, null);
        when(generate.generate(conversationId, user.id())).thenReturn(pending);
        when(get.get(conversationId, quizId, user.id())).thenReturn(completed);

        QuizCreatedResponse created = controller.create(user, conversationId);
        QuizResponse found = controller.get(user, conversationId, quizId);

        assertThat(created).isEqualTo(new QuizCreatedResponse(quizId, QuizStatus.PENDING));
        assertThat(found.status()).isEqualTo(QuizStatus.COMPLETED);
        assertThat(found.questions()).singleElement().satisfies(q -> {
            assertThat(q.question()).isEqualTo("Q");
            assertThat(q.correctIndex()).isEqualTo(1);
        });
        assertThat(found.errorMessage()).isNull();
    }
}
