package tech.buildrun.notebooklm.core.port.in;

import tech.buildrun.notebooklm.core.domain.conversation.Conversation;

import java.util.List;
import java.util.UUID;

public interface ListConversationsPort {

    List<ConversationSummary> execute(UUID notebookId, UUID ownerId);

    record ConversationSummary(Conversation conversation, String preview) {
    }
}
