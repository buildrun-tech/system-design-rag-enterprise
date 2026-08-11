package tech.buildrun.notebooklm.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;
import tech.buildrun.notebooklm.entity.Conversation;
import tech.buildrun.notebooklm.entity.ConversationMessage;
import tech.buildrun.notebooklm.entity.MessageRole;
import tech.buildrun.notebooklm.entity.Notebook;
import tech.buildrun.notebooklm.entity.User;
import tech.buildrun.notebooklm.exception.ConversationNotFoundException;
import tech.buildrun.notebooklm.repository.ConversationMessageRepository;
import tech.buildrun.notebooklm.repository.ConversationRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
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
class ConversationMessageServiceTest {

    @Mock
    private ConversationRepository conversationRepository;

    @Mock
    private ConversationMessageRepository conversationMessageRepository;

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

        when(conversationRepository.findByIdAndNotebook_Owner_Id(conversationId, ownerId))
                .thenReturn(Optional.of(conversation));
        when(conversationMessageRepository.findTop10ByConversation_IdOrderByCreatedAtDesc(conversationId))
                .thenReturn(new ArrayList<>(List.of(previousAssistantMessage, previousUserMessage)));
        when(conversationMessageRepository.save(any(ConversationMessage.class)))
                .thenAnswer(this::assignIdAndReturn);

        AtomicReference<Prompt> capturedPrompt = new AtomicReference<>();
        ChatModel fakeChatModel = fakeChatModelStreaming(capturedPrompt, "De ", "acordo");
        ChatClient chatClient = ChatClient.create(fakeChatModel);

        ConversationMessageService service = new ConversationMessageService(
                conversationRepository, conversationMessageRepository, chatClient);

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
        when(conversationRepository.findByIdAndNotebook_Owner_Id(conversationId, ownerId))
                .thenReturn(Optional.empty());

        ConversationMessageService service = new ConversationMessageService(
                conversationRepository, conversationMessageRepository, mock(ChatClient.class));

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

        when(conversationRepository.findByIdAndNotebook_Owner_Id(conversationId, ownerId))
                .thenReturn(Optional.of(conversation));
        when(conversationMessageRepository.findTop10ByConversation_IdOrderByCreatedAtDesc(conversationId))
                .thenReturn(new ArrayList<>());
        when(conversationMessageRepository.save(any(ConversationMessage.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ChatModel failingChatModel = mock(ChatModel.class);
        when(failingChatModel.getOptions()).thenReturn(ChatOptions.builder().build());
        when(failingChatModel.stream(any(Prompt.class)))
                .thenReturn(Flux.error(new RuntimeException("provider timeout")));
        ChatClient chatClient = ChatClient.create(failingChatModel);

        ConversationMessageService service = new ConversationMessageService(
                conversationRepository, conversationMessageRepository, chatClient);

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

        when(conversationRepository.findByIdAndNotebook_Owner_Id(conversationId, ownerId))
                .thenReturn(Optional.of(conversation));
        when(conversationMessageRepository.findTop10ByConversation_IdOrderByCreatedAtDesc(conversationId))
                .thenReturn(new ArrayList<>());
        when(conversationMessageRepository.save(any(ConversationMessage.class)))
                .thenAnswer(this::assignIdAndReturn);

        AtomicReference<Prompt> capturedPrompt = new AtomicReference<>();
        ChatModel fakeChatModel = fakeChatModelStreaming(capturedPrompt);
        ChatClient chatClient = ChatClient.create(fakeChatModel);

        ConversationMessageService service = new ConversationMessageService(
                conversationRepository, conversationMessageRepository, chatClient);

        SseEmitter emitter = mock(SseEmitter.class);
        org.mockito.Mockito.doThrow(new java.io.IOException("broken pipe"))
                .when(emitter).send(any(SseEmitter.SseEventBuilder.class));

        service.sendMessage(conversationId, ownerId, "hi", emitter);

        verify(emitter, timeout(5000)).completeWithError(any(java.io.IOException.class));
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
