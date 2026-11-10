package tech.buildrun.notebooklm.core.domain.source;

import java.util.UUID;

public record IngestionRequest(UUID sourceId, UUID userId) {
}
