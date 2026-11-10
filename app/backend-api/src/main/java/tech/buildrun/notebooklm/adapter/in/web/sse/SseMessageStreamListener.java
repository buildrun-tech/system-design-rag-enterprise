package tech.buildrun.notebooklm.adapter.in.web.sse;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tech.buildrun.notebooklm.adapter.in.web.dto.ToolEventPayload;
import tech.buildrun.notebooklm.core.domain.conversation.ConversationMessage;
import tech.buildrun.notebooklm.core.domain.conversation.ToolEvent;
import tech.buildrun.notebooklm.core.domain.exception.StreamAbortedException;
import tech.buildrun.notebooklm.core.port.in.MessageStreamListener;

import java.io.IOException;
import java.util.Map;

public class SseMessageStreamListener implements MessageStreamListener {

    private final SseEmitter emitter;

    public SseMessageStreamListener(SseEmitter emitter) {
        this.emitter = emitter;
    }

    @Override
    public void onStart(Runnable cancel) {
        emitter.onCompletion(cancel);
        emitter.onError(error -> cancel.run());
        emitter.onTimeout(() -> {
            cancel.run();
            emitter.complete();
        });
    }

    @Override
    public void onToken(String token) {
        try {
            emitter.send(SseEmitter.event().data(Map.of("token", token)));
        } catch (IOException | IllegalStateException error) {
            emitter.completeWithError(error);
            throw new StreamAbortedException(error);
        }
    }

    @Override
    public void onToolCall(ToolEvent event) {
        try {
            emitter.send(SseEmitter.event().data(Map.of("toolCall", ToolEventPayload.from(event))));
        } catch (IOException | IllegalStateException ignored) {
        }
    }

    @Override
    public void onDone(ConversationMessage assistantMessage) {
        try {
            emitter.send(SseEmitter.event().data(
                    Map.of("done", true, "messageId", assistantMessage.id(), "sources", assistantMessage.sources())));
            emitter.complete();
        } catch (IOException | IllegalStateException error) {
            emitter.completeWithError(error);
        }
    }

    @Override
    public void onError(Throwable error) {
        try {
            emitter.send(SseEmitter.event().data(Map.of("error", "STREAM_ERROR")));
        } catch (IOException | IllegalStateException ignored) {
        } finally {
            emitter.completeWithError(error);
        }
    }
}
