package tech.buildrun.notebooklm.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ConversationDetailResponse(
        UUID id,
        UUID notebookId,
        List<UUID> activeSourceIds,
        Instant createdAt
) {
}
