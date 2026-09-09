package tech.buildrun.notebooklm.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import tech.buildrun.notebooklm.entity.Conversation;
import tech.buildrun.notebooklm.entity.ConversationMessage;
import tech.buildrun.notebooklm.entity.MessageRole;
import tech.buildrun.notebooklm.entity.Notebook;
import tech.buildrun.notebooklm.entity.SourceStatus;
import tech.buildrun.notebooklm.entity.User;
import tech.buildrun.notebooklm.exception.ConversationNotFoundException;
import tech.buildrun.notebooklm.repository.ConversationMessageRepository;
import tech.buildrun.notebooklm.repository.ConversationRepository;
import tech.buildrun.notebooklm.repository.SourceRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ConversationMessageServiceTest {

    @Mock
    private ConversationRepository conversationRepository;

    @Mock
    private ConversationMessageRepository conversationMessageRepository;

    @Mock
    private SourceRepository sourceRepository;

    @Mock
    private VectorStore vectorStore;

    private RetrievalAugmentationAdvisor retrievalAugmentationAdvisor;
    private ConversationHistoryProvider conversationHistoryProvider;

    @BeforeEach
    void setUp() {
        when(vectorStore.similaritySearch(any(org.springframework.ai.vectorstore.SearchRequest.class)))
                .thenReturn(List.of());
        when(sourceRepository.findByNotebookIdAndStatus(any(UUID.class), any(SourceStatus.class)))
                .thenReturn(List.of());
        retrievalAugmentationAdvisor = RetrievalAugmentationAdvisor.builder()
                .documentRetriever(VectorStoreDocumentRetriever.builder().vectorStore(vectorStore).build())
                .queryAugmenter(org.springframework.ai.rag.generation.augmentation.ContextualQueryAugmenter.builder()
                        .allowEmptyContext(true)
                        .build())
                .build();
        conversationHistoryProvider = new ConversationHistoryProvider(conversationMessageRepository);
    }

    @Test
    void sendMessageIncludesHistoryPersistsUserMessageBeforeCallAndAssistantMessageAfterComplete() throws Exception {
        User owner = new User("sub", "owner@test.com", "Owner");
        Notebook notebook = new Notebook(owner, "Notebook", null);
        Conversation conversation = new Conversation(notebook);
        UUID conversationId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        ReflectionTestUtils.setField(conversation, "id", conversationId);

        ConversationMessage previousUserMessage = new ConversationMessage(conversation, MessageRole.user, "previous question");
        ConversationMessage previousAssistantMessage = new ConversationMessage(conversation, MessageRole.assistant, "previous answer");

        when(conversationRepository.findByIdAndNotebookOwnerId(conversationId, ownerId))
                .thenReturn(Optional.of(conversation));
        when(conversationMessageRepository.findTop10ByConversationIdOrderByCreatedAtDesc(conversationId))
                .thenReturn(new ArrayList<>(List.of(previousAssistantMessage, previousUserMessage)));
        when(conversationMessageRepository.save(any(ConversationMessage.class)))
                .thenAnswer(this::assignIdAndReturn);

        AtomicReference<Prompt> capturedPrompt = new AtomicReference<>();
        ChatModel fakeChatModel = fakeChatModelStreaming(capturedPrompt, "De ", "acordo");
        ChatClient chatClient = ChatClient.create(fakeChatModel);

        ConversationMessageService service = new ConversationMessageService(
                conversationRepository, conversationMessageRepository, sourceRepository, chatClient,
                retrievalAugmentationAdvisor, conversationHistoryProvider);

        SseEmitter emitter = mock(SseEmitter.class);

        service.sendMessage(conversationId, ownerId, "new question", emitter);

        verify(emitter, timeout(5000)).complete();

        List<Message> promptMessages = capturedPrompt.get().getInstructions();
        assertThat(promptMessages).hasSize(4);
        assertThat(promptMessages.get(0).getMessageType()).isEqualTo(MessageType.SYSTEM);
        assertThat(promptMessages.get(1).getMessageType()).isEqualTo(MessageType.USER);
        assertThat(promptMessages.get(1).getText()).isEqualTo("previous question");
        assertThat(promptMessages.get(2).getMessageType()).isEqualTo(MessageType.ASSISTANT);
        assertThat(promptMessages.get(2).getText()).isEqualTo("previous answer");
        assertThat(promptMessages.get(3).getMessageType()).isEqualTo(MessageType.USER);
        // sem query transformers configurados no teste e sem chunks (vectorStore mockado vazio),
        // allowEmptyContext(true) mantém a query original sem alteração
        assertThat(promptMessages.get(3).getText()).isEqualTo("new question");

        verify(emitter, org.mockito.Mockito.times(3)).send(any(SseEmitter.SseEventBuilder.class));

        ArgumentCaptor<ConversationMessage> savedCaptor = ArgumentCaptor.forClass(ConversationMessage.class);
        verify(conversationMessageRepository, org.mockito.Mockito.times(2)).save(savedCaptor.capture());

        ConversationMessage savedUserMessage = savedCaptor.getAllValues().get(0);
        assertThat(savedUserMessage.getRole()).isEqualTo(MessageRole.user);
        assertThat(savedUserMessage.getContent()).isEqualTo("new question");

        ConversationMessage savedAssistantMessage = savedCaptor.getAllValues().get(1);
        assertThat(savedAssistantMessage.getRole()).isEqualTo(MessageRole.assistant);
        assertThat(savedAssistantMessage.getContent()).isEqualTo("De acordo");
    }

    @Test
    void sendMessageThrowsWhenConversationNotOwnedByUser() {
        UUID conversationId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        when(conversationRepository.findByIdAndNotebookOwnerId(conversationId, ownerId))
                .thenReturn(Optional.empty());

        ConversationMessageService service = new ConversationMessageService(
                conversationRepository, conversationMessageRepository, sourceRepository,
                mock(ChatClient.class), retrievalAugmentationAdvisor, conversationHistoryProvider);

        assertThatThrownBy(() -> service.sendMessage(conversationId, ownerId, "hi", new SseEmitter()))
                .isInstanceOf(ConversationNotFoundException.class);

        verify(conversationMessageRepository, never()).save(any());
    }

    @Test
    void sendMessageSendsStreamErrorAndDoesNotPersistAssistantMessageWhenLlmFails() throws Exception {
        User owner = new User("sub", "owner@test.com", "Owner");
        Notebook notebook = new Notebook(owner, "Notebook", null);
        Conversation conversation = new Conversation(notebook);
        UUID conversationId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        ReflectionTestUtils.setField(conversation, "id", conversationId);

        when(conversationRepository.findByIdAndNotebookOwnerId(conversationId, ownerId))
                .thenReturn(Optional.of(conversation));
        when(conversationMessageRepository.findTop10ByConversationIdOrderByCreatedAtDesc(conversationId))
                .thenReturn(new ArrayList<>());
        when(conversationMessageRepository.save(any(ConversationMessage.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ChatModel failingChatModel = mock(ChatModel.class);
        when(failingChatModel.getOptions()).thenReturn(ChatOptions.builder().build());
        when(failingChatModel.stream(any(Prompt.class)))
                .thenReturn(Flux.error(new RuntimeException("provider timeout")));
        ChatClient chatClient = ChatClient.create(failingChatModel);

        ConversationMessageService service = new ConversationMessageService(
                conversationRepository, conversationMessageRepository, sourceRepository, chatClient,
                retrievalAugmentationAdvisor, conversationHistoryProvider);

        SseEmitter emitter = mock(SseEmitter.class);

        service.sendMessage(conversationId, ownerId, "hi", emitter);

        verify(emitter, timeout(5000)).completeWithError(any());
        verify(emitter).send(any(SseEmitter.SseEventBuilder.class));
        verify(conversationMessageRepository, org.mockito.Mockito.times(1)).save(any());
    }

    @Test
    void onStreamCompleteFallsBackToCompleteWithErrorWhenSendFails() throws Exception {
        User owner = new User("sub", "owner@test.com", "Owner");
        Notebook notebook = new Notebook(owner, "Notebook", null);
        Conversation conversation = new Conversation(notebook);
        UUID conversationId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        ReflectionTestUtils.setField(conversation, "id", conversationId);

        when(conversationRepository.findByIdAndNotebookOwnerId(conversationId, ownerId))
                .thenReturn(Optional.of(conversation));
        when(conversationMessageRepository.findTop10ByConversationIdOrderByCreatedAtDesc(conversationId))
                .thenReturn(new ArrayList<>());
        when(conversationMessageRepository.save(any(ConversationMessage.class)))
                .thenAnswer(this::assignIdAndReturn);

        AtomicReference<Prompt> capturedPrompt = new AtomicReference<>();
        ChatModel fakeChatModel = fakeChatModelStreaming(capturedPrompt);
        ChatClient chatClient = ChatClient.create(fakeChatModel);

        ConversationMessageService service = new ConversationMessageService(
                conversationRepository, conversationMessageRepository, sourceRepository, chatClient,
                retrievalAugmentationAdvisor, conversationHistoryProvider);

        SseEmitter emitter = mock(SseEmitter.class);
        org.mockito.Mockito.doThrow(new java.io.IOException("broken pipe"))
                .when(emitter).send(any(SseEmitter.SseEventBuilder.class));

        service.sendMessage(conversationId, ownerId, "hi", emitter);

        verify(emitter, timeout(5000)).completeWithError(any(java.io.IOException.class));
    }

    @Test
    void buildActiveSourcesFilterUsesActiveSourcesWhenPresent() {
        User owner = new User("sub", "owner@test.com", "Owner");
        Notebook notebook = new Notebook(owner, "Notebook", null);
        Conversation conversation = new Conversation(notebook);
        tech.buildrun.notebooklm.entity.Source source = new tech.buildrun.notebooklm.entity.Source(
                notebook, "doc.pdf", tech.buildrun.notebooklm.entity.SourceType.FILE, "key", null);
        UUID sourceId = UUID.randomUUID();
        ReflectionTestUtils.setField(source, "id", sourceId);
        conversation.getActiveSources().add(source);

        ConversationMessageService service = new ConversationMessageService(
                conversationRepository, conversationMessageRepository, sourceRepository,
                mock(ChatClient.class), retrievalAugmentationAdvisor, conversationHistoryProvider);

        String filter = service.buildActiveSourcesFilter(conversation);

        assertThat(filter).isEqualTo("source_id in ['" + sourceId + "']");
    }

    @ParameterizedTest
    @ValueSource(strings = {"completion", "timeout", "error"})
    void emitterTerminationCancelsGenerationWithoutSavingPartialResponse(String termination) throws Exception {
        Sinks.Many<ChatResponse> tokens = Sinks.many().unicast().onBackpressureBuffer();
        CountDownLatch subscribed = new CountDownLatch(1);
        CountDownLatch cancelled = new CountDownLatch(1);
        SseEmitter emitter = mock(SseEmitter.class);
        startStream(tokens.asFlux().doOnSubscribe(s -> subscribed.countDown()).doOnCancel(cancelled::countDown), emitter);
        assertThat(subscribed.await(5, TimeUnit.SECONDS)).isTrue();

        if (termination.equals("error")) {
            ArgumentCaptor<Consumer<Throwable>> callback = ArgumentCaptor.forClass(Consumer.class);
            verify(emitter).onError(callback.capture());
            callback.getValue().accept(new java.io.IOException("client disconnected"));
        } else {
            ArgumentCaptor<Runnable> callback = ArgumentCaptor.forClass(Runnable.class);
            if (termination.equals("timeout")) {
                verify(emitter).onTimeout(callback.capture());
            } else {
                verify(emitter).onCompletion(callback.capture());
            }
            callback.getValue().run();
        }

        assertThat(cancelled.await(5, TimeUnit.SECONDS)).isTrue();
        verify(emitter, never()).send(any(SseEmitter.SseEventBuilder.class));
        verify(conversationMessageRepository, org.mockito.Mockito.times(1)).save(any());
        if (termination.equals("timeout")) verify(emitter).complete();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void failedTokenWriteCancelsGenerationWithoutTryingToSendAnError(boolean alreadyCompleted) throws Exception {
        Sinks.Many<ChatResponse> tokens = Sinks.many().unicast().onBackpressureBuffer();
        CountDownLatch subscribed = new CountDownLatch(1);
        CountDownLatch cancelled = new CountDownLatch(1);
        SseEmitter emitter = mock(SseEmitter.class);
        Exception failure = alreadyCompleted
                ? new IllegalStateException("ResponseBodyEmitter has already completed")
                : new java.io.IOException("broken pipe");
        org.mockito.Mockito.doThrow(failure).when(emitter).send(any(SseEmitter.SseEventBuilder.class));
        startStream(tokens.asFlux().doOnSubscribe(s -> subscribed.countDown()).doOnCancel(cancelled::countDown), emitter);
        assertThat(subscribed.await(5, TimeUnit.SECONDS)).isTrue();

        tokens.tryEmitNext(new ChatResponse(List.of(new Generation(new AssistantMessage("partial")))));

        assertThat(cancelled.await(5, TimeUnit.SECONDS)).isTrue();
        verify(emitter, timeout(5000)).completeWithError(failure);
        verify(emitter, org.mockito.Mockito.times(1)).send(any(SseEmitter.SseEventBuilder.class));
        verify(conversationMessageRepository, org.mockito.Mockito.times(1)).save(any());
    }

    @Test
    void errorNotificationRacingWithCompletionPreservesOriginalFailure() throws Exception {
        SseEmitter emitter = mock(SseEmitter.class);
        org.mockito.Mockito.doThrow(new IllegalStateException("ResponseBodyEmitter has already completed"))
                .when(emitter).send(any(SseEmitter.SseEventBuilder.class));
        RuntimeException providerError = new RuntimeException("provider failed");

        startStream(Flux.error(providerError), emitter);

        ArgumentCaptor<Throwable> failure = ArgumentCaptor.forClass(Throwable.class);
        verify(emitter, timeout(5000)).completeWithError(failure.capture());
        assertThat(failure.getValue()).hasRootCause(providerError);
        verify(conversationMessageRepository, org.mockito.Mockito.times(1)).save(any());
    }

    @Test
    void completionBeforeSubscriptionAssignmentStillCancelsGeneration() throws Exception {
        SseEmitter emitter = mock(SseEmitter.class);
        org.mockito.Mockito.doAnswer(invocation -> {
            invocation.<Runnable>getArgument(0).run();
            return null;
        }).when(emitter).onCompletion(any());

        startStream(Flux.just(new ChatResponse(List.of(new Generation(new AssistantMessage("late token"))))), emitter);

        verify(emitter, never()).send(any(SseEmitter.SseEventBuilder.class));
        verify(conversationMessageRepository, org.mockito.Mockito.times(1)).save(any());
    }

    private void startStream(Flux<ChatResponse> responses, SseEmitter emitter) {
        User owner = new User("sub", "owner@test.com", "Owner");
        Conversation conversation = new Conversation(new Notebook(owner, "Notebook", null));
        UUID conversationId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        ReflectionTestUtils.setField(conversation, "id", conversationId);
        when(conversationRepository.findByIdAndNotebookOwnerId(conversationId, ownerId))
                .thenReturn(Optional.of(conversation));
        when(conversationMessageRepository.findTop10ByConversationIdOrderByCreatedAtDesc(conversationId))
                .thenReturn(new ArrayList<>());
        when(conversationMessageRepository.save(any(ConversationMessage.class))).thenAnswer(this::assignIdAndReturn);
        ChatModel model = mock(ChatModel.class);
        when(model.getOptions()).thenReturn(ChatOptions.builder().build());
        when(model.stream(any(Prompt.class))).thenReturn(responses);
        new ConversationMessageService(conversationRepository, conversationMessageRepository, sourceRepository,
                ChatClient.create(model), retrievalAugmentationAdvisor, conversationHistoryProvider)
                .sendMessage(conversationId, ownerId, "hi", emitter);
    }

    @Test
    void buildActiveSourcesFilterFallsBackToReadySourcesWhenNoneActive() {
        User owner = new User("sub", "owner@test.com", "Owner");
        Notebook notebook = new Notebook(owner, "Notebook", null);
        ReflectionTestUtils.setField(notebook, "id", UUID.randomUUID());
        Conversation conversation = new Conversation(notebook);

        tech.buildrun.notebooklm.entity.Source readySource = new tech.buildrun.notebooklm.entity.Source(
                notebook, "doc.pdf", tech.buildrun.notebooklm.entity.SourceType.FILE, "key", null);
        UUID sourceId = UUID.randomUUID();
        ReflectionTestUtils.setField(readySource, "id", sourceId);
        when(sourceRepository.findByNotebookIdAndStatus(notebook.getId(), SourceStatus.READY))
                .thenReturn(List.of(readySource));

        ConversationMessageService service = new ConversationMessageService(
                conversationRepository, conversationMessageRepository, sourceRepository,
                mock(ChatClient.class), retrievalAugmentationAdvisor, conversationHistoryProvider);

        String filter = service.buildActiveSourcesFilter(conversation);

        assertThat(filter).isEqualTo("source_id in ['" + sourceId + "']");
    }

    @Test
    void buildActiveSourcesFilterReturnsUnmatchableFilterWhenNoSourcesAtAll() {
        User owner = new User("sub", "owner@test.com", "Owner");
        Notebook notebook = new Notebook(owner, "Notebook", null);
        ReflectionTestUtils.setField(notebook, "id", UUID.randomUUID());
        Conversation conversation = new Conversation(notebook);

        when(sourceRepository.findByNotebookIdAndStatus(notebook.getId(), SourceStatus.READY))
                .thenReturn(List.of());

        ConversationMessageService service = new ConversationMessageService(
                conversationRepository, conversationMessageRepository, sourceRepository,
                mock(ChatClient.class), retrievalAugmentationAdvisor, conversationHistoryProvider);

        String filter = service.buildActiveSourcesFilter(conversation);

        assertThat(filter).isEqualTo("source_id == 'none'");
    }

    private ConversationMessage assignIdAndReturn(org.mockito.invocation.InvocationOnMock invocation) {
        ConversationMessage message = invocation.getArgument(0);
        if (message.getId() == null) {
            ReflectionTestUtils.setField(message, "id", UUID.randomUUID());
        }
        return message;
    }

    private ChatModel fakeChatModelStreaming(AtomicReference<Prompt> capturedPrompt, String... chunks) {
        return new ChatModel() {
            @Override
            public ChatResponse call(Prompt prompt) {
                throw new UnsupportedOperationException("not used in streaming test");
            }

            @Override
            public Flux<ChatResponse> stream(Prompt prompt) {
                capturedPrompt.set(prompt);
                return Flux.fromArray(chunks)
                        .map(chunk -> new ChatResponse(List.of(new Generation(new AssistantMessage(chunk)))));
            }

            @Override
            public ChatOptions getOptions() {
                return ChatOptions.builder().build();
            }

            @Override
            public ChatOptions getDefaultOptions() {
                return ChatOptions.builder().build();
            }
        };
    }
}
