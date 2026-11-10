package tech.buildrun.notebooklm.core.port.in;

import tech.buildrun.notebooklm.core.domain.conversation.ConversationMessage;

import java.util.List;
import java.util.UUID;

public interface ListMessagesPort {

    List<ConversationMessage> execute(UUID conversationId, UUID ownerId);
}
