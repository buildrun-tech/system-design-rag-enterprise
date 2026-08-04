package tech.buildrun.notebooklm.dto;

import java.time.Instant;
import java.util.UUID;

public record ConversationResponse(
        UUID id,
        UUID notebookId,
        Instant createdAt,
        String preview
) {
}
