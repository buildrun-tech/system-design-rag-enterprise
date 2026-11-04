package tech.buildrun.notebooklm.service;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ChunkEnrichmentServiceTest {

    @Test
    void enrichNotifiesProgressPerChunkAndKeepsOrderAndMetadata() {
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class)))
                .thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage("text")))));
        AtomicInteger progress = new AtomicInteger();

        List<Document> enriched = new ChunkEnrichmentService(chatModel).enrich(
                List.of(new Document("one"), new Document("two"), new Document("three")), progress::incrementAndGet);

        assertThat(progress).hasValue(3);
        assertThat(enriched).extracting(Document::getText).containsExactly("one", "two", "three");
        assertThat(enriched).allSatisfy(chunk -> assertThat(chunk.getMetadata())
                .containsKeys(ChunkEnrichmentService.KEYWORDS_KEY, ChunkEnrichmentService.SUMMARY_KEY));
    }

    @Test
    void enrichStopsAtFirstFailureWithoutNotifyingProgressForIt() {
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class))).thenThrow(new IllegalStateException("402"));
        AtomicInteger progress = new AtomicInteger();

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> new ChunkEnrichmentService(chatModel)
                        .enrich(List.of(new Document("one"), new Document("two")), progress::incrementAndGet))
                .isInstanceOf(IllegalStateException.class);

        assertThat(progress).hasValue(0);
    }
}
