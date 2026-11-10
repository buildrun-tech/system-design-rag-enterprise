package tech.buildrun.notebooklm.core.port.in;

import java.util.UUID;

public interface DeleteConversationPort {

    void execute(UUID notebookId, UUID conversationId, UUID ownerId);
}
