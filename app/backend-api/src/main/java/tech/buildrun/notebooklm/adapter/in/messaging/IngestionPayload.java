package tech.buildrun.notebooklm.adapter.in.messaging;

import java.util.UUID;

public record IngestionPayload(UUID sourceId, String requestId, UUID userId) {
}
