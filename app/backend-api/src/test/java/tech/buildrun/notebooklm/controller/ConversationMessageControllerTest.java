package tech.buildrun.notebooklm.controller;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tech.buildrun.notebooklm.dto.ConversationMessageCreateRequest;
import tech.buildrun.notebooklm.entity.User;
import tech.buildrun.notebooklm.service.ConversationMessageService;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ConversationMessageControllerTest {

    @Test
    void createReturnsEmitterWiredToServiceWithTimeoutCallbackThatCompletesIt() throws Exception {
        ConversationMessageService service = mock(ConversationMessageService.class);
        ConversationMessageController controller = new ConversationMessageController(service);
        User user = new User("sub", "owner@test.com", "Owner");
        UUID conversationId = UUID.randomUUID();

        SseEmitter emitter = controller.create(user, conversationId, new ConversationMessageCreateRequest("hi"));

        assertThat(emitter).isNotNull();
        verify(service).sendMessage(eq(conversationId), eq(user.getId()), eq("hi"), same(emitter));

        Object timeoutCallback = ReflectionTestUtils.getField(emitter, "timeoutCallback");
        ((Runnable) timeoutCallback).run();

        assertThat((Boolean) ReflectionTestUtils.getField(emitter, "complete")).isTrue();
    }
}
