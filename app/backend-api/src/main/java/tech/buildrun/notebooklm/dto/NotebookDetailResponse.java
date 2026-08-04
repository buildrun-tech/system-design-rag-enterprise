package tech.buildrun.notebooklm.dto;

import tech.buildrun.notebooklm.entity.Notebook;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record NotebookDetailResponse(
        UUID id,
        String name,
        String description,
        Instant createdAt,
        Instant updatedAt,
        List<SourceResponse> sources
) {

    public static NotebookDetailResponse from(Notebook notebook, List<SourceResponse> sources) {
        return new NotebookDetailResponse(
                notebook.getId(),
                notebook.getName(),
                notebook.getDescription(),
                notebook.getCreatedAt(),
                notebook.getUpdatedAt(),
                sources
        );
    }
}
