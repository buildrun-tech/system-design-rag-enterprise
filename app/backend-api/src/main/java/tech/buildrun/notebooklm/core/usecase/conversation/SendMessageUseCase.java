package tech.buildrun.notebooklm.core.usecase.conversation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tech.buildrun.notebooklm.core.domain.conversation.CitedSource;
import tech.buildrun.notebooklm.core.domain.conversation.Conversation;
import tech.buildrun.notebooklm.core.domain.conversation.ConversationMessage;
import tech.buildrun.notebooklm.core.domain.conversation.MessageRole;
import tech.buildrun.notebooklm.core.domain.conversation.ToolEvent;
import tech.buildrun.notebooklm.core.domain.exception.ConversationNotFoundException;
import tech.buildrun.notebooklm.core.domain.source.Source;
import tech.buildrun.notebooklm.core.domain.source.SourceStatus;
import tech.buildrun.notebooklm.core.port.in.MessageStreamListener;
import tech.buildrun.notebooklm.core.port.in.SendMessagePort;
import tech.buildrun.notebooklm.core.port.out.ai.ChatCompletionPort;
import tech.buildrun.notebooklm.core.port.out.ai.ChatStreamListener;
import tech.buildrun.notebooklm.core.port.out.persistence.ConversationMessageRepositoryPort;
import tech.buildrun.notebooklm.core.port.out.persistence.ConversationRepositoryPort;
import tech.buildrun.notebooklm.core.port.out.persistence.SourceRepositoryPort;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class SendMessageUseCase implements SendMessagePort {

    private static final Logger log = LoggerFactory.getLogger(SendMessageUseCase.class);

    private final ConversationRepositoryPort conversations;
    private final ConversationMessageRepositoryPort messages;
    private final SourceRepositoryPort sources;
    private final ChatCompletionPort chat;

    public SendMessageUseCase(ConversationRepositoryPort conversations, ConversationMessageRepositoryPort messages,
                              SourceRepositoryPort sources, ChatCompletionPort chat) {
        this.conversations = conversations;
        this.messages = messages;
        this.sources = sources;
        this.chat = chat;
    }

    @Override
    public void send(UUID conversationId, UUID ownerId, String content, MessageStreamListener listener) {
        Conversation conversation = conversations.findByIdAndOwnerId(conversationId, ownerId)
                .orElseThrow(ConversationNotFoundException::new);

        List<ConversationMessage> history = new ArrayList<>(messages.findTop10ByConversationIdOrderByCreatedAtDesc(conversationId));
        Collections.reverse(history);
        List<UUID> sourceIds = activeSourceIds(conversation);

        messages.save(conversation.append(MessageRole.user, content));

        chat.stream(history, content, sourceIds, new AssistantReplyUseCase(conversation, listener));
    }

    private List<UUID> activeSourceIds(Conversation conversation) {
        return conversation.hasActiveSources()
                ? List.copyOf(conversation.activeSourceIds())
                : sources.findByNotebookIdAndStatus(conversation.notebookId(), SourceStatus.READY)
                        .stream().map(Source::id).toList();
    }

    private final class AssistantReplyUseCase implements ChatStreamListener {

        private final Conversation conversation;
        private final MessageStreamListener listener;
        private final StringBuilder response = new StringBuilder();

        private AssistantReplyUseCase(Conversation conversation, MessageStreamListener listener) {
            this.conversation = conversation;
            this.listener = listener;
        }

        @Override
        public void onStart(Runnable cancel) {
            listener.onStart(cancel);
        }

        @Override
        public void onToken(String token) {
            listener.onToken(token);
            response.append(token);
        }

        @Override
        public void onToolCall(ToolEvent event) {
            listener.onToolCall(event);
        }

        @Override
        public void onComplete(List<CitedSource> citedSources) {
            ConversationMessage saved;
            try {
                saved = messages.save(conversation.append(MessageRole.assistant, response.toString(), citedSources));
            } catch (RuntimeException error) {
                listener.onError(error);
                return;
            }
            log.info("SSE stream out: conversationId={} messageId={} responseChars={} sources={}",
                    conversation.id(), saved.id(), response.length(), citedSources.size());
            listener.onDone(saved);
        }

        @Override
        public void onError(Throwable error) {
            log.error("SSE stream failed: conversationId={}", conversation.id(), error);
            listener.onError(error);
        }
    }
}
