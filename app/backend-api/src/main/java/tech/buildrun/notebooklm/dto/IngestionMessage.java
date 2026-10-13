package tech.buildrun.notebooklm.dto;

import java.util.UUID;

public record IngestionMessage(UUID sourceId, String requestId, UUID userId) {

    public IngestionMessage(UUID sourceId) {
        this(sourceId, null, null);
    }
}
