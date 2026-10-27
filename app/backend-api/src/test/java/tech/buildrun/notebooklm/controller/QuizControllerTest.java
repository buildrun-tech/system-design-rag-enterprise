package tech.buildrun.notebooklm.controller;

import org.junit.jupiter.api.Test;
import tech.buildrun.notebooklm.dto.QuizCreatedResponse;
import tech.buildrun.notebooklm.dto.QuizResponse;
import tech.buildrun.notebooklm.entity.QuizStatus;
import tech.buildrun.notebooklm.entity.User;
import tech.buildrun.notebooklm.service.QuizService;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class QuizControllerTest {

    @Test
    void createAndGetDelegateToServiceWithCurrentUser() {
        QuizService service = mock(QuizService.class);
        QuizController controller = new QuizController(service);
        User user = new User("sub", "owner@test.com", "Owner");
        UUID conversationId = UUID.randomUUID();
        UUID quizId = UUID.randomUUID();
        QuizCreatedResponse created = new QuizCreatedResponse(quizId, QuizStatus.PENDING);
        QuizResponse found = new QuizResponse(quizId, QuizStatus.PROCESSING, null, null);
        when(service.create(conversationId, user.getId())).thenReturn(created);
        when(service.get(conversationId, quizId, user.getId())).thenReturn(found);

        assertThat(controller.create(user, conversationId)).isSameAs(created);
        assertThat(controller.get(user, conversationId, quizId)).isSameAs(found);
    }
}
