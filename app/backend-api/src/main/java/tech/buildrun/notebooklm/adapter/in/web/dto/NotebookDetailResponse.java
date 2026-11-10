package tech.buildrun.notebooklm.adapter.in.web.dto;

import tech.buildrun.notebooklm.core.domain.source.SourceStatus;
import tech.buildrun.notebooklm.core.domain.source.SourceType;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record NotebookDetailResponse(
        UUID id,
        String name,
        String description,
        Instant createdAt,
        Instant updatedAt,
        List<SourceItem> sources
) {

    public record SourceItem(
            UUID id,
            String name,
            SourceType type,
            SourceStatus status,
            String errorMessage,
            Instant createdAt
    ) {
    }
}
