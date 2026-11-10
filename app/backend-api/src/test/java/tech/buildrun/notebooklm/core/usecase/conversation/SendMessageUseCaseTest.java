package tech.buildrun.notebooklm.core.usecase.conversation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.buildrun.notebooklm.core.domain.conversation.CitedChunk;
import tech.buildrun.notebooklm.core.domain.conversation.CitedSource;
import tech.buildrun.notebooklm.core.domain.conversation.Conversation;
import tech.buildrun.notebooklm.core.domain.conversation.ConversationMessage;
import tech.buildrun.notebooklm.core.domain.conversation.MessageRole;
import tech.buildrun.notebooklm.core.domain.conversation.ToolEvent;
import tech.buildrun.notebooklm.core.domain.exception.ConversationNotFoundException;
import tech.buildrun.notebooklm.core.domain.source.Source;
import tech.buildrun.notebooklm.core.domain.source.SourceStatus;
import tech.buildrun.notebooklm.core.domain.source.SourceType;
import tech.buildrun.notebooklm.core.port.in.MessageStreamListener;
import tech.buildrun.notebooklm.core.port.out.ai.ChatCompletionPort;
import tech.buildrun.notebooklm.core.port.out.ai.ChatStreamListener;
import tech.buildrun.notebooklm.core.port.out.persistence.ConversationMessageRepositoryPort;
import tech.buildrun.notebooklm.core.port.out.persistence.ConversationRepositoryPort;
import tech.buildrun.notebooklm.core.port.out.persistence.SourceRepositoryPort;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SendMessageUseCaseTest {

    private final UUID conversationId = UUID.randomUUID();
    private final UUID notebookId = UUID.randomUUID();
    private final UUID ownerId = UUID.randomUUID();

    private ConversationRepositoryPort conversations;
    private ConversationMessageRepositoryPort messages;
    private SourceRepositoryPort sources;
    private final List<ConversationMessage> saved = new ArrayList<>();
    private final List<String> events = new ArrayList<>();
    private FakeChat chat;
    private SendMessageUseCase useCase;
    private MessageStreamListener listener;

    @BeforeEach
    void setUp() {
        conversations = mock(ConversationRepositoryPort.class);
        messages = mock(ConversationMessageRepositoryPort.class);
        sources = mock(SourceRepositoryPort.class);
        chat = new FakeChat();
        useCase = new SendMessageUseCase(conversations, messages, sources, chat);
        when(messages.save(any())).thenAnswer(invocation -> {
            ConversationMessage m = invocation.getArgument(0);
            ConversationMessage persisted = ConversationMessage.restore(UUID.randomUUID(), m.conversationId(),
                    m.role(), m.content(), m.sources(), null);
            saved.add(persisted);
            events.add("save:" + m.role());
            return persisted;
        });
        when(messages.findTop10ByConversationIdOrderByCreatedAtDesc(conversationId)).thenReturn(new ArrayList<>());
        listener = new RecordingListener();
    }

    private Conversation conversation(Set<UUID> active) {
        Conversation c = Conversation.restore(conversationId, notebookId, active, null);
        when(conversations.findByIdAndOwnerId(conversationId, ownerId)).thenReturn(Optional.of(c));
        return c;
    }

    @Test
    void persistsUserMessageThenStreamsThenPersistsAssistantWithSources() {
        conversation(Set.of());
        ConversationMessage previousUser = ConversationMessage.restore(UUID.randomUUID(), conversationId, MessageRole.user, "q1", List.of(), null);
        ConversationMessage previousAssistant = ConversationMessage.restore(UUID.randomUUID(), conversationId, MessageRole.assistant, "a1", List.of(), null);
        when(messages.findTop10ByConversationIdOrderByCreatedAtDesc(conversationId))
                .thenReturn(new ArrayList<>(List.of(previousAssistant, previousUser)));
        UUID readyId = UUID.randomUUID();
        when(sources.findByNotebookIdAndStatus(notebookId, SourceStatus.READY))
                .thenReturn(List.of(Source.restore(readyId, notebookId, "doc.pdf", SourceType.FILE, "k", null, SourceStatus.READY, null, null)));
        CitedSource cited = new CitedSource(readyId, "doc.pdf", List.of(new CitedChunk(0, "txt")));
        chat.script = l -> {
            l.onToolCall(new ToolEvent("1", "t", "T", ToolEvent.Status.start, "{}", null, null, null));
            l.onToken("De ");
            l.onToken("acordo");
            l.onComplete(List.of(cited));
        };

        useCase.send(conversationId, ownerId, "new question", listener);

        assertThat(chat.history).extracting(ConversationMessage::content).containsExactly("q1", "a1");
        assertThat(chat.question).isEqualTo("new question");
        assertThat(chat.sourceIds).containsExactly(readyId);
        assertThat(saved).extracting(ConversationMessage::role).containsExactly(MessageRole.user, MessageRole.assistant);
        assertThat(saved.get(0).content()).isEqualTo("new question");
        assertThat(saved.get(1).content()).isEqualTo("De acordo");
        assertThat(saved.get(1).sources()).containsExactly(cited);
        assertThat(events).containsExactly("save:user", "start", "toolCall", "token:De ", "token:acordo", "save:assistant", "done");
    }

    @Test
    void usesActiveSourcesWhenPresent() {
        UUID active = UUID.randomUUID();
        conversation(Set.of(active));
        chat.script = l -> { };

        useCase.send(conversationId, ownerId, "hi", listener);

        assertThat(chat.sourceIds).containsExactly(active);
        verify(sources, never()).findByNotebookIdAndStatus(any(), any());
    }

    @Test
    void throwsWhenConversationNotOwnedByUser() {
        when(conversations.findByIdAndOwnerId(conversationId, ownerId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.send(conversationId, ownerId, "hi", listener))
                .isInstanceOf(ConversationNotFoundException.class);

        verify(messages, never()).save(any());
    }

    @Test
    void forwardsErrorAndDoesNotPersistAssistantWhenLlmFails() {
        conversation(Set.of());
        chat.script = l -> l.onError(new RuntimeException("provider timeout"));

        useCase.send(conversationId, ownerId, "hi", listener);

        assertThat(events).containsExactly("save:user", "start", "error");
        assertThat(saved).hasSize(1);
    }

    @Test
    void reportsErrorWhenAssistantMessageCannotBeSaved() {
        conversation(Set.of());
        chat.script = l -> {
            org.mockito.Mockito.doThrow(new IllegalStateException("db down")).when(messages).save(any());
            l.onToken("x");
            l.onComplete(List.of());
        };

        useCase.send(conversationId, ownerId, "hi", listener);

        assertThat(events).containsExactly("save:user", "start", "token:x", "error");
    }

    private final class RecordingListener implements MessageStreamListener {
        public void onStart(Runnable cancel) { events.add("start"); }
        public void onToken(String token) { events.add("token:" + token); }
        public void onToolCall(ToolEvent event) { events.add("toolCall"); }
        public void onDone(ConversationMessage assistantMessage) { events.add("done"); }
        public void onError(Throwable error) { events.add("error"); }
    }

    private static final class FakeChat implements ChatCompletionPort {
        java.util.function.Consumer<ChatStreamListener> script = l -> { };
        List<ConversationMessage> history;
        String question;
        List<UUID> sourceIds;

        @Override
        public void stream(List<ConversationMessage> history, String question, List<UUID> sourceIds, ChatStreamListener listener) {
            this.history = history;
            this.question = question;
            this.sourceIds = sourceIds;
            listener.onStart(() -> { });
            script.accept(listener);
        }
    }
}
