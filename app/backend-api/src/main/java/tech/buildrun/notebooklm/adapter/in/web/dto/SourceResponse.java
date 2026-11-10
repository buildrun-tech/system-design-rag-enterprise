package tech.buildrun.notebooklm.adapter.in.web.dto;

import tech.buildrun.notebooklm.core.domain.source.SourceStatus;
import tech.buildrun.notebooklm.core.domain.source.SourceType;

import java.time.Instant;
import java.util.UUID;

public record SourceResponse(
        UUID id,
        String name,
        SourceType type,
        SourceStatus status,
        String errorMessage,
        Instant createdAt
) {
}
