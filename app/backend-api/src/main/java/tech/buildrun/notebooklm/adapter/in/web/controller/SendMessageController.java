package tech.buildrun.notebooklm.adapter.in.web.controller;

import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tech.buildrun.notebooklm.adapter.in.web.dto.ConversationMessageCreateRequest;
import tech.buildrun.notebooklm.adapter.in.web.security.CurrentUser;
import tech.buildrun.notebooklm.adapter.in.web.sse.SseMessageStreamListener;
import tech.buildrun.notebooklm.core.domain.user.User;
import tech.buildrun.notebooklm.core.port.in.SendMessagePort;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/conversations/{conversationId}/messages")
public class SendMessageController {

    private static final long STREAM_TIMEOUT_MS = 60_000L;

    private final SendMessagePort sendMessage;

    public SendMessageController(SendMessagePort sendMessage) {
        this.sendMessage = sendMessage;
    }

    @PostMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter create(@CurrentUser User currentUser,
                             @PathVariable UUID conversationId,
                             @Valid @RequestBody ConversationMessageCreateRequest request) {
        SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT_MS);
        sendMessage.send(conversationId, currentUser.id(), request.content(), new SseMessageStreamListener(emitter));
        return emitter;
    }
}
