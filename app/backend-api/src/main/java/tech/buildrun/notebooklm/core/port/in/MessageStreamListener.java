package tech.buildrun.notebooklm.core.port.in;

import tech.buildrun.notebooklm.core.domain.conversation.ConversationMessage;
import tech.buildrun.notebooklm.core.domain.conversation.ToolEvent;

public interface MessageStreamListener {

    void onStart(Runnable cancel);

    void onToken(String token);

    void onToolCall(ToolEvent event);

    void onDone(ConversationMessage assistantMessage);

    void onError(Throwable error);
}
