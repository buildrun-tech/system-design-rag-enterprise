package tech.buildrun.notebooklm.core.port.out.ai;

import tech.buildrun.notebooklm.core.domain.conversation.ConversationMessage;

import java.util.List;
import java.util.UUID;

public interface ChatCompletionPort {

    void stream(List<ConversationMessage> history, String question, List<UUID> sourceIds, ChatStreamListener listener);
}
