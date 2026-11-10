package tech.buildrun.notebooklm.adapter.out.ai.tool;

import tech.buildrun.notebooklm.core.domain.conversation.ToolEvent;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;

import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

public class EventingToolCallback implements ToolCallback {

    public static final String LISTENER_KEY = "toolEvents";
    private static final int MAX_PAYLOAD_CHARS = 2000;

    private final ToolCallback delegate;

    public EventingToolCallback(ToolCallback delegate) {
        this.delegate = delegate;
    }

    public static ToolCallback[] wrap(ToolCallback... callbacks) {
        return Arrays.stream(callbacks).map(EventingToolCallback::new).toArray(ToolCallback[]::new);
    }

    @Override
    public ToolDefinition getToolDefinition() {
        return delegate.getToolDefinition();
    }

    @Override
    public ToolMetadata getToolMetadata() {
        return delegate.getToolMetadata();
    }

    @Override
    public String call(String toolInput) {
        return call(toolInput, null);
    }

    @Override
    public String call(String toolInput, ToolContext toolContext) {
        Consumer<ToolEvent> listener = listener(toolContext);
        if (listener == null) return delegate.call(toolInput, toolContext);

        String id = UUID.randomUUID().toString();
        ToolDefinition definition = getToolDefinition();
        String name = definition.name();
        Optional<ToolMessages.Texts> texts = ToolMessages.forTool(name);
        String label = texts.map(ToolMessages.Texts::label).orElseGet(() -> label(definition.description(), name));
        long startedAt = System.nanoTime();
        listener.accept(new ToolEvent(id, name, label, ToolEvent.Status.start, toolInput, null, null, null));
        try {
            String result = delegate.call(toolInput, toolContext);
            listener.accept(new ToolEvent(id, name, label, ToolEvent.Status.end, null, truncate(result), elapsedMs(startedAt),
                    texts.map(ToolMessages.Texts::ok).orElse(null)));
            return result;
        } catch (RuntimeException error) {
            listener.accept(new ToolEvent(id, name, label, ToolEvent.Status.error, null, truncate(error.getMessage()), elapsedMs(startedAt),
                    texts.map(ToolMessages.Texts::error).orElse(null)));
            throw error;
        }
    }

    static String label(String description, String fallback) {
        if (description == null || description.isBlank()) return fallback;
        String text = description.strip();
        int end = text.indexOf(". ");
        String sentence = end > 0 ? text.substring(0, end) : text;
        return sentence.endsWith(".") ? sentence.substring(0, sentence.length() - 1) : sentence;
    }

    private static String truncate(String text) {
        if (text == null || text.length() <= MAX_PAYLOAD_CHARS) return text;
        return text.substring(0, MAX_PAYLOAD_CHARS) + "…";
    }

    private static long elapsedMs(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }

    @SuppressWarnings("unchecked")
    private static Consumer<ToolEvent> listener(ToolContext toolContext) {
        if (toolContext == null) return null;
        return toolContext.getContext().get(LISTENER_KEY) instanceof Consumer<?> consumer
                ? (Consumer<ToolEvent>) consumer
                : null;
    }
}
