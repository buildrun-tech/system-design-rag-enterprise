package tech.buildrun.notebooklm.adapter.in.web.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import tech.buildrun.notebooklm.adapter.in.web.dto.ConversationCreateRequest;
import tech.buildrun.notebooklm.adapter.in.web.dto.ConversationDetailResponse;
import tech.buildrun.notebooklm.adapter.in.web.dto.ConversationResponse;
import tech.buildrun.notebooklm.adapter.in.web.mapper.ConversationWebMapper;
import tech.buildrun.notebooklm.adapter.in.web.security.CurrentUser;
import tech.buildrun.notebooklm.core.domain.user.User;
import tech.buildrun.notebooklm.core.port.in.CreateConversationPort;
import tech.buildrun.notebooklm.core.port.in.DeleteConversationPort;
import tech.buildrun.notebooklm.core.port.in.ListConversationsPort;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/notebooks/{notebookId}/conversations")
public class ConversationController {

    private final CreateConversationPort createConversation;
    private final ListConversationsPort listConversations;
    private final DeleteConversationPort deleteConversation;

    public ConversationController(CreateConversationPort createConversation,
                                  ListConversationsPort listConversations,
                                  DeleteConversationPort deleteConversation) {
        this.createConversation = createConversation;
        this.listConversations = listConversations;
        this.deleteConversation = deleteConversation;
    }

    @GetMapping
    public List<ConversationResponse> list(@CurrentUser User currentUser, @PathVariable UUID notebookId) {
        return listConversations.execute(notebookId, currentUser.id()).stream()
                .map(ConversationWebMapper::toResponse)
                .toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ConversationDetailResponse create(@CurrentUser User currentUser, @PathVariable UUID notebookId,
                                             @RequestBody ConversationCreateRequest request) {
        return ConversationWebMapper.toDetailResponse(
                createConversation.execute(notebookId, currentUser.id(), request.activeSourceIds()));
    }

    @DeleteMapping("/{conversationId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@CurrentUser User currentUser, @PathVariable UUID notebookId,
                       @PathVariable UUID conversationId) {
        deleteConversation.execute(notebookId, conversationId, currentUser.id());
    }
}
