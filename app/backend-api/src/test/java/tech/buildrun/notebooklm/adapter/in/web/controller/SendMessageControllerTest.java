package tech.buildrun.notebooklm.adapter.in.web.controller;

import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tech.buildrun.notebooklm.adapter.in.web.dto.ConversationMessageCreateRequest;
import tech.buildrun.notebooklm.core.domain.user.User;
import tech.buildrun.notebooklm.core.port.in.SendMessagePort;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class SendMessageControllerTest {

    @Test
    void createReturnsEmitterWiredToPortWithSixtySecondTimeout() {
        SendMessagePort port = mock(SendMessagePort.class);
        SendMessageController controller = new SendMessageController(port);
        User user = User.restore(UUID.randomUUID(), "sub", "owner@test.com", "Owner", Instant.now());
        UUID conversationId = UUID.randomUUID();

        SseEmitter emitter = controller.create(user, conversationId, new ConversationMessageCreateRequest("hi"));

        assertThat(emitter.getTimeout()).isEqualTo(60_000L);
        verify(port).send(eq(conversationId), eq(user.id()), eq("hi"), any());
    }
}
