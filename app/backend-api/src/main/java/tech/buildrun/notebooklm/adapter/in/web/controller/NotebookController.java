package tech.buildrun.notebooklm.adapter.in.web.controller;

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
import tech.buildrun.notebooklm.adapter.in.web.dto.NotebookDetailResponse;
import tech.buildrun.notebooklm.adapter.in.web.dto.NotebookPatchRequest;
import tech.buildrun.notebooklm.adapter.in.web.dto.NotebookRequest;
import tech.buildrun.notebooklm.adapter.in.web.dto.NotebookResponse;
import tech.buildrun.notebooklm.adapter.in.web.mapper.NotebookWebMapper;
import tech.buildrun.notebooklm.adapter.in.web.security.CurrentUser;
import tech.buildrun.notebooklm.core.domain.shared.PageQuery;
import tech.buildrun.notebooklm.core.domain.user.User;
import tech.buildrun.notebooklm.core.port.in.CreateNotebookPort;
import tech.buildrun.notebooklm.core.port.in.DeleteNotebookPort;
import tech.buildrun.notebooklm.core.port.in.GetNotebookPort;
import tech.buildrun.notebooklm.core.port.in.ListNotebooksPort;
import tech.buildrun.notebooklm.core.port.in.PatchNotebookPort;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/notebooks")
public class NotebookController {

    private final CreateNotebookPort createNotebook;
    private final PatchNotebookPort patchNotebook;
    private final ListNotebooksPort listNotebooks;
    private final GetNotebookPort getNotebook;
    private final DeleteNotebookPort deleteNotebook;

    public NotebookController(CreateNotebookPort createNotebook, PatchNotebookPort patchNotebook,
                              ListNotebooksPort listNotebooks, GetNotebookPort getNotebook,
                              DeleteNotebookPort deleteNotebook) {
        this.createNotebook = createNotebook;
        this.patchNotebook = patchNotebook;
        this.listNotebooks = listNotebooks;
        this.getNotebook = getNotebook;
        this.deleteNotebook = deleteNotebook;
    }

    @GetMapping
    public Page<NotebookResponse> list(@CurrentUser User currentUser,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        var result = listNotebooks.execute(currentUser.id(),
                new PageQuery(pageable.getPageNumber(), pageable.getPageSize(),
                        pageable.getSort().stream()
                                .map(order -> new PageQuery.Order(order.getProperty(), order.isAscending()))
                                .toList()));
        return NotebookWebMapper.toPage(result, pageable);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public NotebookResponse create(@CurrentUser User currentUser, @Valid @RequestBody NotebookRequest request) {
        return NotebookWebMapper.toResponse(
                createNotebook.execute(currentUser.id(), request.name(), request.description()));
    }

    @GetMapping("/{notebookId}")
    public NotebookDetailResponse get(@CurrentUser User currentUser, @PathVariable UUID notebookId) {
        return NotebookWebMapper.toDetailResponse(getNotebook.execute(notebookId, currentUser.id()));
    }

    @PatchMapping("/{notebookId}")
    public NotebookResponse update(@CurrentUser User currentUser, @PathVariable UUID notebookId,
                                   @Valid @RequestBody NotebookPatchRequest request) {
        return NotebookWebMapper.toResponse(
                patchNotebook.execute(notebookId, currentUser.id(), request.name(), request.description()));
    }

    @DeleteMapping("/{notebookId}")
    public ResponseEntity<Void> delete(@CurrentUser User currentUser, @PathVariable UUID notebookId) {
        deleteNotebook.execute(notebookId, currentUser.id());
        return ResponseEntity.noContent().build();
    }
}
