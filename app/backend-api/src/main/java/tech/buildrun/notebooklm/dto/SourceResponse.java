package tech.buildrun.notebooklm.dto;

import tech.buildrun.notebooklm.entity.Source;
import tech.buildrun.notebooklm.entity.SourceStatus;
import tech.buildrun.notebooklm.entity.SourceType;

import java.time.Instant;
import java.util.UUID;

public record SourceResponse(
        UUID id,
        String name,
        SourceType type,
        SourceStatus status,
        Instant createdAt
) {

    public static SourceResponse from(Source source) {
        return new SourceResponse(
                source.getId(),
                source.getName(),
                source.getType(),
                source.getStatus(),
                source.getCreatedAt()
        );
    }
}
