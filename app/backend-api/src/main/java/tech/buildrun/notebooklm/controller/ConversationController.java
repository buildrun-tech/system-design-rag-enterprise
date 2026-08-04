package tech.buildrun.notebooklm.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import tech.buildrun.notebooklm.dto.ConversationCreateRequest;
import tech.buildrun.notebooklm.dto.ConversationDetailResponse;
import tech.buildrun.notebooklm.dto.ConversationResponse;
import tech.buildrun.notebooklm.entity.User;
import tech.buildrun.notebooklm.security.CurrentUser;
import tech.buildrun.notebooklm.service.ConversationService;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/notebooks/{notebookId}/conversations")
public class ConversationController {

    private final ConversationService conversationService;

    public ConversationController(ConversationService conversationService) {
        this.conversationService = conversationService;
    }

    @GetMapping
    public List<ConversationResponse> list(@CurrentUser User currentUser, @PathVariable UUID notebookId) {
        return conversationService.listByNotebook(notebookId, currentUser.getId());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ConversationDetailResponse create(@CurrentUser User currentUser, @PathVariable UUID notebookId,
                                              @RequestBody ConversationCreateRequest request) {
        return conversationService.create(notebookId, currentUser.getId(), request);
    }
}
