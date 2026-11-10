package tech.buildrun.notebooklm.adapter.in.web.dto;

import java.time.Instant;
import java.util.UUID;

public record NotebookResponse(
        UUID id,
        String name,
        String description,
        Instant createdAt,
        Instant updatedAt
) {
}
