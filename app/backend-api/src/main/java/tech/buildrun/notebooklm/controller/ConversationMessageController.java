package tech.buildrun.notebooklm.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tech.buildrun.notebooklm.dto.ConversationMessageResponse;
import tech.buildrun.notebooklm.entity.User;
import tech.buildrun.notebooklm.security.CurrentUser;
import tech.buildrun.notebooklm.service.ConversationMessageService;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/conversations/{conversationId}/messages")
public class ConversationMessageController {

    private final ConversationMessageService conversationMessageService;

    public ConversationMessageController(ConversationMessageService conversationMessageService) {
        this.conversationMessageService = conversationMessageService;
    }

    @GetMapping
    public List<ConversationMessageResponse> list(@CurrentUser User currentUser, @PathVariable UUID conversationId) {
        return conversationMessageService.listByConversation(conversationId, currentUser.getId());
    }
}
