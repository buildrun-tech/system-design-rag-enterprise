package tech.buildrun.notebooklm.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import tech.buildrun.notebooklm.dto.SourceResponse;
import tech.buildrun.notebooklm.entity.User;
import tech.buildrun.notebooklm.security.CurrentUser;
import tech.buildrun.notebooklm.service.SourceService;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/notebooks/{notebookId}/sources")
public class SourceController {

    private final SourceService sourceService;

    public SourceController(SourceService sourceService) {
        this.sourceService = sourceService;
    }

    @PostMapping(consumes = "multipart/form-data")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public SourceResponse upload(@CurrentUser User currentUser, @PathVariable UUID notebookId,
                                  @RequestParam("file") MultipartFile file) {
        return sourceService.upload(notebookId, currentUser.getId(), file);
    }

    @GetMapping
    public List<SourceResponse> list(@CurrentUser User currentUser, @PathVariable UUID notebookId) {
        return sourceService.listByNotebook(notebookId, currentUser.getId());
    }

    @GetMapping("/{sourceId}")
    public SourceResponse get(@CurrentUser User currentUser, @PathVariable UUID notebookId,
                               @PathVariable UUID sourceId) {
        return sourceService.get(notebookId, sourceId, currentUser.getId());
    }

    @DeleteMapping("/{sourceId}")
    public ResponseEntity<Void> delete(@CurrentUser User currentUser, @PathVariable UUID notebookId,
                                        @PathVariable UUID sourceId) {
        sourceService.delete(notebookId, sourceId, currentUser.getId());
        return ResponseEntity.noContent().build();
    }
}
