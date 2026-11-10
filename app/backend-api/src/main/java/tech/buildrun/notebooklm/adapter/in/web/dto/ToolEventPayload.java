package tech.buildrun.notebooklm.adapter.in.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import tech.buildrun.notebooklm.core.domain.conversation.ToolEvent;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ToolEventPayload(String id, String name, String label, ToolEvent.Status status,
                               String input, String result, Long durationMs, String summary) {

    public static ToolEventPayload from(ToolEvent event) {
        return new ToolEventPayload(event.id(), event.name(), event.label(), event.status(),
                event.input(), event.result(), event.durationMs(), event.summary());
    }
}
