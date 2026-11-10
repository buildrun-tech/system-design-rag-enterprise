package tech.buildrun.notebooklm.adapter.out.queue;

import java.util.UUID;

public record IngestionMessage(UUID sourceId, String requestId, UUID userId) {
}
