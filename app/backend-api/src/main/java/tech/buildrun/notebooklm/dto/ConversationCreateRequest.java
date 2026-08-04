package tech.buildrun.notebooklm.dto;

import java.util.List;
import java.util.UUID;

public record ConversationCreateRequest(
        List<UUID> activeSourceIds
) {
}
