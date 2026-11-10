package tech.buildrun.notebooklm.adapter.in.web.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tech.buildrun.notebooklm.adapter.in.web.dto.ConversationMessageResponse;
import tech.buildrun.notebooklm.adapter.in.web.mapper.ConversationWebMapper;
import tech.buildrun.notebooklm.adapter.in.web.security.CurrentUser;
import tech.buildrun.notebooklm.core.domain.user.User;
import tech.buildrun.notebooklm.core.port.in.ListMessagesPort;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/conversations/{conversationId}/messages")
public class ListMessagesController {

    private final ListMessagesPort listMessages;

    public ListMessagesController(ListMessagesPort listMessages) {
        this.listMessages = listMessages;
    }

    @GetMapping
    public List<ConversationMessageResponse> list(@CurrentUser User currentUser, @PathVariable UUID conversationId) {
        return listMessages.execute(conversationId, currentUser.id()).stream()
                .map(ConversationWebMapper::toResponse)
                .toList();
    }
}
