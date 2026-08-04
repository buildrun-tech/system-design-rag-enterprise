package tech.buildrun.notebooklm.dto;

import tech.buildrun.notebooklm.entity.Notebook;

import java.time.Instant;
import java.util.UUID;

public record NotebookResponse(
        UUID id,
        String name,
        String description,
        Instant createdAt,
        Instant updatedAt
) {

    public static NotebookResponse from(Notebook notebook) {
        return new NotebookResponse(
                notebook.getId(),
                notebook.getName(),
                notebook.getDescription(),
                notebook.getCreatedAt(),
                notebook.getUpdatedAt()
        );
    }
}
