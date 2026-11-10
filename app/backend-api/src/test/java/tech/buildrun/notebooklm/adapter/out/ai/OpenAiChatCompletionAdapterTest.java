package tech.buildrun.notebooklm.adapter.out.ai;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.rag.generation.augmentation.ContextualQueryAugmenter;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import tech.buildrun.notebooklm.adapter.out.ai.tool.DateTimeTools;
import tech.buildrun.notebooklm.adapter.out.ai.tool.EventingToolCallback;
import tech.buildrun.notebooklm.core.domain.conversation.CitedSource;
import tech.buildrun.notebooklm.core.domain.conversation.ConversationMessage;
import tech.buildrun.notebooklm.core.domain.conversation.MessageRole;
import tech.buildrun.notebooklm.core.domain.conversation.ToolEvent;
import tech.buildrun.notebooklm.core.domain.exception.StreamAbortedException;
import tech.buildrun.notebooklm.core.domain.source.Source;
import tech.buildrun.notebooklm.core.domain.source.SourceStatus;
import tech.buildrun.notebooklm.core.domain.source.SourceType;
import tech.buildrun.notebooklm.core.port.out.ai.ChatStreamListener;
import tech.buildrun.notebooklm.core.port.out.persistence.SourceRepositoryPort;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpenAiChatCompletionAdapterTest {

    private SourceRepositoryPort sources;
    private RetrievalAugmentationAdvisor advisor;
    private final List<String> events = new CopyOnWriteArrayList<>();
    private final List<ToolEvent> retrievalEvents = new CopyOnWriteArrayList<>();
    private final List<String> order = new CopyOnWriteArrayList<>();
    private final AtomicReference<Runnable> cancelHandle = new AtomicReference<>();
    private final AtomicReference<List<CitedSource>> completedSources = new AtomicReference<>();
    private boolean abortOnToken;

    private final ChatStreamListener listener = new ChatStreamListener() {
        public void onStart(Runnable cancel) { cancelHandle.set(cancel); }
        public void onToken(String token) {
            events.add("token:" + token);
            order.add("token");
            if (abortOnToken) throw new StreamAbortedException(new RuntimeException("broken pipe"));
        }
        public void onToolCall(ToolEvent event) {
            if ("retrieval".equals(event.name())) {
                retrievalEvents.add(event);
                order.add("retrieval:" + event.status());
            } else {
                events.add("tool:" + event.status());
            }
        }
        public void onComplete(List<CitedSource> s) { completedSources.set(s); events.add("complete"); }
        public void onError(Throwable error) { events.add("error:" + error.getMessage()); }
    };

    @BeforeEach
    void setUp() {
        VectorStore vectorStore = mock(VectorStore.class);
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());
        sources = mock(SourceRepositoryPort.class);
        advisor = RetrievalAugmentationAdvisor.builder()
                .documentRetriever(VectorStoreDocumentRetriever.builder().vectorStore(vectorStore).build())
                .queryAugmenter(ContextualQueryAugmenter.builder().allowEmptyContext(true).build())
                .build();
    }

    private OpenAiChatCompletionAdapter adapter(ChatClient client) {
        return new OpenAiChatCompletionAdapter(client, advisor, sources);
    }

    private ChatModel model(Flux<ChatResponse> responses, AtomicReference<Prompt> captured) {
        ChatModel model = mock(ChatModel.class);
        when(model.getOptions()).thenReturn(ChatOptions.builder().build());
        when(model.stream(any(Prompt.class))).thenAnswer(invocation -> {
            captured.set(invocation.getArgument(0));
            return responses;
        });
        return model;
    }

    private static ChatResponse token(String text) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
    }

    private static ConversationMessage message(MessageRole role, String content) {
        return ConversationMessage.restore(UUID.randomUUID(), UUID.randomUUID(), role, content, List.of(), null);
    }

    @Test
    void emitsRetrievalStepBeforeFirstToken() {
        ChatClient client = ChatClient.create(model(Flux.just(token("a"), token("b")), new AtomicReference<>()));

        adapter(client).stream(List.of(), "q", List.of(), listener);

        await().atMost(5, TimeUnit.SECONDS).until(() -> events.contains("complete"));
        assertThat(order).containsExactly("retrieval:start", "retrieval:end", "token", "token");
        assertThat(retrievalEvents).extracting(ToolEvent::label).containsOnly("Buscando nas suas fontes");
        assertThat(retrievalEvents.get(0).id()).isEqualTo(retrievalEvents.get(1).id());
        assertThat(retrievalEvents.get(1).summary()).isEqualTo("Nenhum trecho relevante encontrado");
        assertThat(retrievalEvents.get(1).durationMs()).isNotNegative();
    }

    @Test
    void retrievalSummaryCountsChunksAndDistinctSources() {
        Document a1 = new Document("x", Map.of("source_id", "s1"));
        Document a2 = new Document("y", Map.of("source_id", "s1"));
        Document b1 = new Document("z", Map.of("source_id", "s2"));

        assertThat(OpenAiChatCompletionAdapter.retrievalSummary(List.of(a1, a2, b1)))
                .isEqualTo("3 trechos encontrados em 2 fontes");
        assertThat(OpenAiChatCompletionAdapter.retrievalSummary(List.of(a1)))
                .isEqualTo("1 trecho encontrado em 1 fonte");
    }

    @Test
    void streamsTokensWithHistoryPromptThenCompletes() {
        AtomicReference<Prompt> prompt = new AtomicReference<>();
        ChatClient client = ChatClient.create(model(Flux.just(token("De "), token("acordo")), prompt));

        adapter(client).stream(List.of(message(MessageRole.user, "q1"), message(MessageRole.assistant, "a1")),
                "new question", List.of(), listener);

        await().atMost(5, TimeUnit.SECONDS).until(() -> events.contains("complete"));
        List<Message> messages = prompt.get().getInstructions();
        assertThat(messages).hasSize(4);
        assertThat(messages.get(0).getMessageType()).isEqualTo(MessageType.SYSTEM);
        assertThat(messages.get(3).getText()).isEqualTo("new question");
        assertThat(events).containsExactly("token:De ", "token:acordo", "complete");
        assertThat(completedSources.get()).isEmpty();
    }

    @Test
    void reportsProviderFailureAsError() {
        ChatClient client = ChatClient.create(model(Flux.error(new RuntimeException("provider timeout")), new AtomicReference<>()));

        adapter(client).stream(List.of(), "hi", List.of(), listener);

        await().atMost(5, TimeUnit.SECONDS).until(() -> !events.isEmpty());
        assertThat(events).hasSize(1);
        assertThat(events.get(0)).startsWith("error:");
    }

    @Test
    void emitsToolEventsBeforeTokens() {
        ChatModel toolRunningModel = new ChatModel() {
            @Override
            public ChatResponse call(Prompt prompt) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Flux<ChatResponse> stream(Prompt prompt) {
                ToolCallingChatOptions options = (ToolCallingChatOptions) prompt.getOptions();
                options.getToolCallbacks().forEach(callback -> callback.call("{}", new ToolContext(options.getToolContext())));
                return Flux.just(token("ok"));
            }

            @Override
            public ChatOptions getOptions() {
                return ToolCallingChatOptions.builder().build();
            }

            @Override
            public ChatOptions getDefaultOptions() {
                return ToolCallingChatOptions.builder().build();
            }
        };
        ChatClient client = ChatClient.builder(toolRunningModel)
                .defaultToolCallbacks(EventingToolCallback.wrap(ToolCallbacks.from(new DateTimeTools())))
                .build();

        adapter(client).stream(List.of(), "que horas sao?", List.of(), listener);

        await().atMost(5, TimeUnit.SECONDS).until(() -> events.contains("complete"));
        assertThat(events).containsExactly("tool:start", "tool:end", "token:ok", "complete");
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void cancelStopsGenerationWithoutCompleting(boolean beforeSubscription) throws Exception {
        Sinks.Many<ChatResponse> tokens = Sinks.many().unicast().onBackpressureBuffer();
        CountDownLatch subscribed = new CountDownLatch(1);
        CountDownLatch cancelled = new CountDownLatch(1);
        ChatClient client = ChatClient.create(model(
                tokens.asFlux().doOnSubscribe(s -> subscribed.countDown()).doOnCancel(cancelled::countDown), new AtomicReference<>()));
        ChatStreamListener cancelling = new ChatStreamListener() {
            public void onStart(Runnable cancel) {
                cancelHandle.set(cancel);
                if (beforeSubscription) cancel.run();
            }
            public void onToken(String token) { events.add("token"); }
            public void onToolCall(ToolEvent event) { }
            public void onComplete(List<CitedSource> s) { events.add("complete"); }
            public void onError(Throwable error) { events.add("error"); }
        };

        adapter(client).stream(List.of(), "hi", List.of(), cancelling);
        if (!beforeSubscription) {
            assertThat(subscribed.await(5, TimeUnit.SECONDS)).isTrue();
            cancelHandle.get().run();
        }

        if (!beforeSubscription) assertThat(cancelled.await(5, TimeUnit.SECONDS)).isTrue();
        tokens.tryEmitNext(token("late"));
        assertThat(events).isEmpty();
    }

    @Test
    void abortedTokenWriteCancelsGenerationWithoutError() throws Exception {
        abortOnToken = true;
        Sinks.Many<ChatResponse> tokens = Sinks.many().unicast().onBackpressureBuffer();
        CountDownLatch subscribed = new CountDownLatch(1);
        CountDownLatch cancelled = new CountDownLatch(1);
        ChatClient client = ChatClient.create(model(
                tokens.asFlux().doOnSubscribe(s -> subscribed.countDown()).doOnCancel(cancelled::countDown), new AtomicReference<>()));

        adapter(client).stream(List.of(), "hi", List.of(), listener);
        assertThat(subscribed.await(5, TimeUnit.SECONDS)).isTrue();
        tokens.tryEmitNext(token("partial"));

        assertThat(cancelled.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(events).containsExactly("token:partial");
    }

    @Test
    void filterExpressionUsesGivenIdsOrUnmatchableWhenEmpty() {
        UUID id = UUID.randomUUID();

        assertThat(OpenAiChatCompletionAdapter.filterExpression(List.of(id))).isEqualTo("source_id in ['" + id + "']");
        assertThat(OpenAiChatCompletionAdapter.filterExpression(List.of())).isEqualTo("source_id == 'none'");
    }

    @Test
    void buildCitedSourcesGroupsChunksBySourceInRelevanceOrderAndSkipsMissingOrUntagged() {
        UUID sourceA = UUID.randomUUID();
        UUID sourceB = UUID.randomUUID();
        Document chunkA0 = chunk("texto A0", sourceA, 0);
        Document chunkB3 = chunk("texto B3", sourceB, 3);
        Document chunkA1 = chunk("texto A1", sourceA, 1);
        Document gone = chunk("texto removido", UUID.randomUUID(), 0);
        Document untagged = new Document("sem source", Map.of());
        when(sources.findAllById(any())).thenReturn(List.of(sourceNamed(sourceA, "doc-a.pdf"), sourceNamed(sourceB, "doc-b.pdf")));

        List<CitedSource> cited = adapter(mock(ChatClient.class)).buildCitedSources(List.of(chunkA0, chunkB3, chunkA1, gone, untagged));

        assertThat(cited).hasSize(2);
        assertThat(cited.get(0).sourceId()).isEqualTo(sourceA);
        assertThat(cited.get(0).name()).isEqualTo("doc-a.pdf");
        assertThat(cited.get(0).chunks()).extracting("chunkIndex").containsExactly(0, 1);
        assertThat(cited.get(0).chunks()).extracting("snippet").containsExactly("texto A0", "texto A1");
        assertThat(cited.get(1).chunks()).extracting("chunkIndex").containsExactly(3);
    }

    @Test
    void buildCitedSourcesReturnsEmptyWithoutDocumentsAndSkipsLookup() {
        assertThat(adapter(mock(ChatClient.class)).buildCitedSources(List.of())).isEmpty();
        verify(sources, never()).findAllById(any());
    }

    @Test
    void snippetTruncatesAtWordBoundaryAndNormalizesWhitespace() {
        assertThat(OpenAiChatCompletionAdapter.snippet("  curto \n texto  ")).isEqualTo("curto texto");

        String snippet = OpenAiChatCompletionAdapter.snippet("palavra ".repeat(100));

        assertThat(snippet).endsWith("palavra…");
        assertThat(snippet.length()).isLessThanOrEqualTo(301);
        assertThat(OpenAiChatCompletionAdapter.snippet(null)).isEmpty();
    }

    private Document chunk(String text, UUID sourceId, int chunkIndex) {
        return new Document(text, Map.of("source_id", sourceId.toString(), "chunk_index", chunkIndex));
    }

    private Source sourceNamed(UUID id, String name) {
        return Source.restore(id, UUID.randomUUID(), name, SourceType.FILE, "key", null, SourceStatus.READY, null, null);
    }
}
