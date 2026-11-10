package tech.buildrun.notebooklm.adapter.in.web.dto;

import tech.buildrun.notebooklm.core.domain.conversation.CitedSource;
import tech.buildrun.notebooklm.core.domain.conversation.MessageRole;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ConversationMessageResponse(
        UUID id,
        MessageRole role,
        String content,
        List<CitedSource> sources,
        Instant createdAt
) {
}
