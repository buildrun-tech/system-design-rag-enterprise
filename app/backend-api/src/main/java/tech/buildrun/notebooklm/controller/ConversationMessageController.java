package tech.buildrun.notebooklm.controller;

import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tech.buildrun.notebooklm.dto.ConversationMessageCreateRequest;
import tech.buildrun.notebooklm.dto.ConversationMessageResponse;
import tech.buildrun.notebooklm.entity.User;
import tech.buildrun.notebooklm.security.CurrentUser;
import tech.buildrun.notebooklm.service.ConversationMessageService;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/conversations/{conversationId}/messages")
public class ConversationMessageController {

    private static final long STREAM_TIMEOUT_MS = 60_000L;

    private final ConversationMessageService conversationMessageService;

    public ConversationMessageController(ConversationMessageService conversationMessageService) {
        this.conversationMessageService = conversationMessageService;
    }

    @GetMapping
    public List<ConversationMessageResponse> list(@CurrentUser User currentUser, @PathVariable UUID conversationId) {
        return conversationMessageService.listByConversation(conversationId, currentUser.getId());
    }

    @PostMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter create(@CurrentUser User currentUser,
                              @PathVariable UUID conversationId,
                              @Valid @RequestBody ConversationMessageCreateRequest request) {
        SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT_MS);
        emitter.onTimeout(emitter::complete);
        conversationMessageService.sendMessage(conversationId, currentUser.getId(), request.content(), emitter);
        return emitter;
    }
}
