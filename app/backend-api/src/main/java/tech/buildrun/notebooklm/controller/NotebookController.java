package tech.buildrun.notebooklm.controller;

import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import tech.buildrun.notebooklm.dto.NotebookDetailResponse;
import tech.buildrun.notebooklm.dto.NotebookPatchRequest;
import tech.buildrun.notebooklm.dto.NotebookRequest;
import tech.buildrun.notebooklm.dto.NotebookResponse;
import tech.buildrun.notebooklm.entity.User;
import tech.buildrun.notebooklm.security.CurrentUser;
import tech.buildrun.notebooklm.service.NotebookService;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/notebooks")
public class NotebookController {

    private final NotebookService notebookService;

    public NotebookController(NotebookService notebookService) {
        this.notebookService = notebookService;
    }

    @GetMapping
    public Page<NotebookResponse> list(@CurrentUser User currentUser,
                                        @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return notebookService.listByOwner(currentUser.getId(), pageable);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public NotebookResponse create(@CurrentUser User currentUser, @Valid @RequestBody NotebookRequest request) {
        return notebookService.create(currentUser, request);
    }

    @GetMapping("/{notebookId}")
    public NotebookDetailResponse get(@CurrentUser User currentUser, @PathVariable UUID notebookId) {
        return notebookService.getDetailOrThrow(notebookId, currentUser.getId());
    }

    @PatchMapping("/{notebookId}")
    public NotebookResponse update(@CurrentUser User currentUser, @PathVariable UUID notebookId,
                                    @Valid @RequestBody NotebookPatchRequest request) {
        return notebookService.update(notebookId, currentUser.getId(), request);
    }

    @DeleteMapping("/{notebookId}")
    public ResponseEntity<Void> delete(@CurrentUser User currentUser, @PathVariable UUID notebookId) {
        notebookService.delete(notebookId, currentUser.getId());
        return ResponseEntity.noContent().build();
    }
}
