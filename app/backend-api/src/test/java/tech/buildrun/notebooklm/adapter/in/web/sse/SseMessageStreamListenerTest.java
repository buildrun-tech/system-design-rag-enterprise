package tech.buildrun.notebooklm.adapter.in.web.sse;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tech.buildrun.notebooklm.core.domain.conversation.ConversationMessage;
import tech.buildrun.notebooklm.core.domain.conversation.MessageRole;
import tech.buildrun.notebooklm.core.domain.conversation.ToolEvent;
import tech.buildrun.notebooklm.core.domain.exception.StreamAbortedException;

import tech.buildrun.notebooklm.adapter.in.web.dto.ToolEventPayload;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class SseMessageStreamListenerTest {

    private final SseEmitter emitter = mock(SseEmitter.class);
    private final SseMessageStreamListener listener = new SseMessageStreamListener(emitter);

    @Test
    void emitterTerminationTriggersCancel() {
        Runnable cancel = mock(Runnable.class);
        listener.onStart(cancel);

        ArgumentCaptor<Runnable> completion = ArgumentCaptor.forClass(Runnable.class);
        verify(emitter).onCompletion(completion.capture());
        completion.getValue().run();
        ArgumentCaptor<Runnable> timeout = ArgumentCaptor.forClass(Runnable.class);
        verify(emitter).onTimeout(timeout.capture());
        timeout.getValue().run();

        verify(cancel, org.mockito.Mockito.times(2)).run();
        verify(emitter).complete();
    }

    @Test
    void tokenWriteFailureCompletesWithErrorAndAborts() throws Exception {
        IOException failure = new IOException("broken pipe");
        doThrow(failure).when(emitter).send(any(SseEmitter.SseEventBuilder.class));

        assertThatThrownBy(() -> listener.onToken("x")).isInstanceOf(StreamAbortedException.class);

        verify(emitter).completeWithError(failure);
    }

    @Test
    void toolCallWriteFailureIsIgnored() throws Exception {
        doThrow(new IOException("x")).when(emitter).send(any(SseEmitter.SseEventBuilder.class));

        listener.onToolCall(new ToolEvent("1", "t", "T", ToolEvent.Status.start, "{}", null, null, null));

        verify(emitter, never()).completeWithError(any());
    }

    @Test
    void doneSendsEventAndCompletes() throws Exception {
        listener.onDone(ConversationMessage.restore(UUID.randomUUID(), UUID.randomUUID(), MessageRole.assistant, "ok", List.of(), null));

        verify(emitter).send(any(SseEmitter.SseEventBuilder.class));
        verify(emitter).complete();
    }

    @Test
    void doneWriteFailureCompletesWithError() throws Exception {
        IOException failure = new IOException("broken pipe");
        doThrow(failure).when(emitter).send(any(SseEmitter.SseEventBuilder.class));

        listener.onDone(ConversationMessage.restore(UUID.randomUUID(), UUID.randomUUID(), MessageRole.assistant, "ok", List.of(), null));

        verify(emitter).completeWithError(failure);
    }

    @Test
    void errorSendsStreamErrorEvenWhenSendRacesWithCompletion() throws Exception {
        doThrow(new IllegalStateException("already completed")).when(emitter).send(any(SseEmitter.SseEventBuilder.class));
        RuntimeException providerError = new RuntimeException("provider failed");

        listener.onError(providerError);

        verify(emitter).completeWithError(providerError);
    }

    @Test
    void toolPayloadCarriesSummaryAndOmitsNulls() throws Exception {
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        var end = new ToolEvent("1", "retrieval", "Buscando nas suas fontes", ToolEvent.Status.end, null, null, 12L,
                "4 trechos encontrados em 2 fontes");
        var start = new ToolEvent("1", "retrieval", "Buscando nas suas fontes", ToolEvent.Status.start, null, null, null, null);

        assertThat(mapper.writeValueAsString(ToolEventPayload.from(end)))
                .contains("\"summary\":\"4 trechos encontrados em 2 fontes\"");
        assertThat(mapper.writeValueAsString(ToolEventPayload.from(start))).doesNotContain("summary");
    }
}
