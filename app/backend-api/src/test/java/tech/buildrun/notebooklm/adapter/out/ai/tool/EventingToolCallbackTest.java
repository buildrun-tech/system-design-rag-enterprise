package tech.buildrun.notebooklm.adapter.out.ai.tool;

import tech.buildrun.notebooklm.core.domain.conversation.ToolEvent;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EventingToolCallbackTest {

    private static ToolCallback tool(java.util.function.Supplier<String> body) {
        ToolDefinition definition = ToolDefinition.builder().name("myTool").description("Faz algo. Use quando precisar.").inputSchema("{}").build();
        return new ToolCallback() {
            @Override
            public ToolDefinition getToolDefinition() {
                return definition;
            }

            @Override
            public String call(String toolInput) {
                return body.get();
            }
        };
    }

    private static ToolContext contextWith(List<ToolEvent> sink) {
        return new ToolContext(Map.of(EventingToolCallback.LISTENER_KEY, (Consumer<ToolEvent>) sink::add));
    }

    @Test
    void emitsStartAndEndWithSameIdAndDuration() {
        List<ToolEvent> events = new ArrayList<>();
        ToolCallback callback = new EventingToolCallback(tool(() -> "result"));

        String result = callback.call("{\"a\":1}", contextWith(events));

        assertThat(result).isEqualTo("result");
        assertThat(events).hasSize(2);
        assertThat(events.get(0).status()).isEqualTo(ToolEvent.Status.start);
        assertThat(events.get(0).name()).isEqualTo("myTool");
        assertThat(events.get(0).durationMs()).isNull();
        assertThat(events.get(0).label()).isEqualTo("Faz algo");
        assertThat(events.get(0).input()).isEqualTo("{\"a\":1}");
        assertThat(events.get(0).result()).isNull();
        assertThat(events.get(1).result()).isEqualTo("result");
        assertThat(events.get(1).status()).isEqualTo(ToolEvent.Status.end);
        assertThat(events.get(1).id()).isEqualTo(events.get(0).id());
        assertThat(events.get(1).durationMs()).isNotNegative();
    }

    @Test
    void emitsErrorAndRethrowsWhenToolFails() {
        List<ToolEvent> events = new ArrayList<>();
        ToolCallback callback = new EventingToolCallback(tool(() -> {
            throw new IllegalStateException("boom");
        }));

        assertThatThrownBy(() -> callback.call("{}", contextWith(events)))
                .isInstanceOf(IllegalStateException.class).hasMessage("boom");

        assertThat(events).extracting(ToolEvent::status)
                .containsExactly(ToolEvent.Status.start, ToolEvent.Status.error);
        assertThat(events.get(1).id()).isEqualTo(events.get(0).id());
        assertThat(events.get(1).result()).isEqualTo("boom");
    }

    @Test
    void labelFallsBackToNameWithoutDescription() {
        assertThat(EventingToolCallback.label(null, "myTool")).isEqualTo("myTool");
        assertThat(EventingToolCallback.label("Uma frase só.", "x")).isEqualTo("Uma frase só");
    }

    @Test
    void eachExecutionGetsDistinctId() {
        List<ToolEvent> events = new ArrayList<>();
        ToolCallback callback = new EventingToolCallback(tool(() -> "x"));

        callback.call("{}", contextWith(events));
        callback.call("{}", contextWith(events));

        assertThat(events.get(0).id()).isNotEqualTo(events.get(2).id());
    }

    @Test
    void justDelegatesWithoutListener() {
        ToolCallback callback = new EventingToolCallback(tool(() -> "result"));

        assertThat(callback.call("{}")).isEqualTo("result");
        assertThat(callback.call("{}", new ToolContext(Map.of()))).isEqualTo("result");
    }

    private static ToolCallback namedTool(String name, java.util.function.Supplier<String> body) {
        ToolDefinition definition = ToolDefinition.builder().name(name).description("Texto técnico para o modelo.").inputSchema("{}").build();
        return new ToolCallback() {
            @Override
            public ToolDefinition getToolDefinition() {
                return definition;
            }

            @Override
            public String call(String toolInput) {
                return body.get();
            }
        };
    }

    @Test
    void knownToolGetsFriendlyLabelAndSummaries() {
        List<ToolEvent> events = new ArrayList<>();
        new EventingToolCallback(namedTool("currentDateTime", () -> "2026-11-09T20:45:00-03:00"))
                .call("{}", contextWith(events));

        assertThat(events.get(0).label()).isEqualTo("Consultando data e hora");
        assertThat(events.get(0).summary()).isNull();
        assertThat(events.get(1).summary()).isEqualTo("Data e hora consultadas");
        assertThat(events.get(1).result()).isEqualTo("2026-11-09T20:45:00-03:00");
    }

    @Test
    void knownToolErrorGetsFriendlySummary() {
        List<ToolEvent> events = new ArrayList<>();
        ToolCallback callback = new EventingToolCallback(namedTool("currentDateTime", () -> {
            throw new IllegalStateException("boom");
        }));

        assertThatThrownBy(() -> callback.call("{}", contextWith(events))).isInstanceOf(IllegalStateException.class);

        assertThat(events.get(1).summary()).isEqualTo("Não foi possível consultar data e hora");
    }

    @Test
    void unknownToolOmitsSummary() {
        List<ToolEvent> events = new ArrayList<>();
        new EventingToolCallback(tool(() -> "r")).call("{}", contextWith(events));

        assertThat(events.get(1).summary()).isNull();
    }
}
