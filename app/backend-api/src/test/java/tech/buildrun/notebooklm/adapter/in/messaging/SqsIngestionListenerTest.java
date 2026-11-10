package tech.buildrun.notebooklm.adapter.in.messaging;

import com.openai.core.http.Headers;
import com.openai.errors.UnexpectedStatusCodeException;
import io.awspring.cloud.sqs.listener.Visibility;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tech.buildrun.notebooklm.core.port.in.IngestSourcePort;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class SqsIngestionListenerTest {

    private final IngestSourcePort ingest = mock(IngestSourcePort.class);
    private final SqsIngestionListener listener = new SqsIngestionListener(ingest);
    private final UUID sourceId = UUID.randomUUID();
    private final IngestionPayload message = new IngestionPayload(sourceId, null, null);

    private static RuntimeException providerError(int status) {
        return new RuntimeException(UnexpectedStatusCodeException.builder().statusCode(status)
                .headers(Headers.builder().build()).build());
    }

    @Test
    void delegatesToUseCase() {
        listener.process(message, null);

        verify(ingest).ingest(eq(sourceId), any(Runnable.class));
    }

    @Test
    void acknowledgesOnPermanentProviderError() {
        doThrow(providerError(402)).when(ingest).ingest(eq(sourceId), any());

        assertThatCode(() -> listener.process(message, null)).doesNotThrowAnyException();
    }

    @Test
    void rethrowsOnTransientProviderError() {
        doThrow(providerError(503)).when(ingest).ingest(eq(sourceId), any());

        assertThatThrownBy(() -> listener.process(message, null)).isInstanceOf(RuntimeException.class);
    }

    @Test
    void rethrowsOnUnknownError() {
        doThrow(new IllegalStateException("x")).when(ingest).ingest(eq(sourceId), any());

        assertThatThrownBy(() -> listener.process(message, null)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void progressExtendsVisibilityAndSwallowsFailures() {
        Visibility visibility = mock(Visibility.class);
        doThrow(new IllegalStateException("sqs down")).when(visibility).changeTo(180);
        doThrow(new IllegalStateException("stop")).when(ingest).ingest(eq(sourceId), any());
        ArgumentCaptor<Runnable> progress = ArgumentCaptor.forClass(Runnable.class);

        assertThatThrownBy(() -> listener.process(message, visibility)).isInstanceOf(IllegalStateException.class);
        verify(ingest).ingest(eq(sourceId), progress.capture());

        assertThatCode(() -> progress.getValue().run()).doesNotThrowAnyException();
        verify(visibility, times(1)).changeTo(180);
    }
}
