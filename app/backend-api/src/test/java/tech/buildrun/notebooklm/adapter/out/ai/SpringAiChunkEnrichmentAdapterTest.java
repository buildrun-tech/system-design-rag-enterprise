package tech.buildrun.notebooklm.adapter.out.ai;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import tech.buildrun.notebooklm.core.domain.chunk.ChunkText;
import tech.buildrun.notebooklm.core.domain.chunk.EnrichedChunk;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SpringAiChunkEnrichmentAdapterTest {

    @Test
    void enrichNotifiesProgressPerChunkAndKeepsOrderAndIds() {
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class)))
                .thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage("text")))));
        AtomicInteger progress = new AtomicInteger();

        List<EnrichedChunk> enriched = new SpringAiChunkEnrichmentAdapter(chatModel).enrich(
                List.of(new ChunkText("1", "one"), new ChunkText("2", "two"), new ChunkText("3", "three")),
                progress::incrementAndGet);

        assertThat(progress).hasValue(3);
        assertThat(enriched).extracting(EnrichedChunk::id).containsExactly("1", "2", "3");
        assertThat(enriched).allSatisfy(chunk -> {
            assertThat(chunk.keywords()).isEqualTo("text");
            assertThat(chunk.summary()).isEqualTo("text");
        });
    }

    @Test
    void enrichStopsAtFirstFailureWithoutNotifyingProgressForIt() {
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class))).thenThrow(new IllegalStateException("402"));
        AtomicInteger progress = new AtomicInteger();

        assertThatThrownBy(() -> new SpringAiChunkEnrichmentAdapter(chatModel).enrich(
                List.of(new ChunkText("1", "one"), new ChunkText("2", "two")), progress::incrementAndGet))
                .isInstanceOf(IllegalStateException.class);

        assertThat(progress).hasValue(0);
    }
}
