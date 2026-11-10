package tech.buildrun.notebooklm.core.port.in;

import tech.buildrun.notebooklm.core.domain.conversation.Conversation;

import java.util.List;
import java.util.UUID;

public interface CreateConversationPort {

    Conversation execute(UUID notebookId, UUID ownerId, List<UUID> activeSourceIds);
}
