package tech.buildrun.notebooklm.adapter.in.web.dto;

import java.util.List;
import java.util.UUID;

public record ConversationCreateRequest(
        List<UUID> activeSourceIds
) {
}
