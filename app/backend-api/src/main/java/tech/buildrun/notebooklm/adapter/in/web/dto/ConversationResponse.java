package tech.buildrun.notebooklm.adapter.in.web.dto;

import java.time.Instant;
import java.util.UUID;

public record ConversationResponse(
        UUID id,
        UUID notebookId,
        Instant createdAt,
        String preview
) {
}
