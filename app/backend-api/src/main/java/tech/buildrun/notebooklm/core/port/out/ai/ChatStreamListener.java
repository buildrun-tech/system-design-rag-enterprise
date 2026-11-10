package tech.buildrun.notebooklm.core.port.out.ai;

import tech.buildrun.notebooklm.core.domain.conversation.CitedSource;
import tech.buildrun.notebooklm.core.domain.conversation.ToolEvent;

import java.util.List;

public interface ChatStreamListener {

    void onStart(Runnable cancel);

    void onToken(String token);

    void onToolCall(ToolEvent event);

    void onComplete(List<CitedSource> sources);

    void onError(Throwable error);
}
