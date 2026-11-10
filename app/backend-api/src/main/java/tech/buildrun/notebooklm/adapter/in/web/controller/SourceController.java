package tech.buildrun.notebooklm.adapter.in.web.controller;

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
import tech.buildrun.notebooklm.adapter.in.web.dto.SourceResponse;
import tech.buildrun.notebooklm.adapter.in.web.mapper.SourceResponseMapper;
import tech.buildrun.notebooklm.adapter.in.web.security.CurrentUser;
import tech.buildrun.notebooklm.core.domain.user.User;
import tech.buildrun.notebooklm.core.port.in.DeleteSourcePort;
import tech.buildrun.notebooklm.core.port.in.GetSourcePort;
import tech.buildrun.notebooklm.core.port.in.ListSourcesPort;
import tech.buildrun.notebooklm.core.port.in.UploadSourcePort;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/notebooks/{notebookId}/sources")
public class SourceController {

    private final UploadSourcePort uploadSource;
    private final ListSourcesPort listSources;
    private final GetSourcePort getSource;
    private final DeleteSourcePort deleteSource;

    public SourceController(UploadSourcePort uploadSource, ListSourcesPort listSources, GetSourcePort getSource,
                            DeleteSourcePort deleteSource) {
        this.uploadSource = uploadSource;
        this.listSources = listSources;
        this.getSource = getSource;
        this.deleteSource = deleteSource;
    }

    @PostMapping(consumes = "multipart/form-data")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public SourceResponse upload(@CurrentUser User currentUser, @PathVariable UUID notebookId,
                                 @RequestParam("file") MultipartFile file) {
        return SourceResponseMapper.toResponse(
                uploadSource.upload(SourceResponseMapper.toCommand(notebookId, currentUser.id(), file)));
    }

    @GetMapping
    public List<SourceResponse> list(@CurrentUser User currentUser, @PathVariable UUID notebookId) {
        return listSources.listByNotebook(notebookId, currentUser.id()).stream()
                .map(SourceResponseMapper::toResponse).toList();
    }

    @GetMapping("/{sourceId}")
    public SourceResponse get(@CurrentUser User currentUser, @PathVariable UUID notebookId,
                              @PathVariable UUID sourceId) {
        return SourceResponseMapper.toResponse(getSource.get(notebookId, sourceId, currentUser.id()));
    }

    @DeleteMapping("/{sourceId}")
    public ResponseEntity<Void> delete(@CurrentUser User currentUser, @PathVariable UUID notebookId,
                                       @PathVariable UUID sourceId) {
        deleteSource.delete(notebookId, sourceId, currentUser.id());
        return ResponseEntity.noContent().build();
    }
}
