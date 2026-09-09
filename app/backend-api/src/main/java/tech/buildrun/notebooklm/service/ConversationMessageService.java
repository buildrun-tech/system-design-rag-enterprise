package tech.buildrun.notebooklm.service;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.Disposable;
import reactor.core.Disposables;
import reactor.core.scheduler.Schedulers;
import tech.buildrun.notebooklm.dto.ConversationMessageResponse;
import tech.buildrun.notebooklm.entity.Conversation;
import tech.buildrun.notebooklm.entity.ConversationMessage;
import tech.buildrun.notebooklm.entity.MessageRole;
import tech.buildrun.notebooklm.entity.Source;
import tech.buildrun.notebooklm.entity.SourceStatus;
import tech.buildrun.notebooklm.exception.ConversationNotFoundException;
import tech.buildrun.notebooklm.repository.ConversationMessageRepository;
import tech.buildrun.notebooklm.repository.ConversationRepository;
import tech.buildrun.notebooklm.repository.SourceRepository;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

@Service
public class ConversationMessageService {

    private static final String SYSTEM_PROMPT =
            "Voce e um assistente que ajuda o usuario a entender os documentos do notebook.";

    private final ConversationRepository conversationRepository;
    private final ConversationMessageRepository conversationMessageRepository;
    private final SourceRepository sourceRepository;
    private final ChatClient chatClient;
    private final RetrievalAugmentationAdvisor retrievalAugmentationAdvisor;
    private final ConversationHistoryProvider conversationHistoryProvider;

    public ConversationMessageService(ConversationRepository conversationRepository,
                                       ConversationMessageRepository conversationMessageRepository,
                                       SourceRepository sourceRepository,
                                       ChatClient chatClient,
                                       RetrievalAugmentationAdvisor retrievalAugmentationAdvisor,
                                       ConversationHistoryProvider conversationHistoryProvider) {
        this.conversationRepository = conversationRepository;
        this.conversationMessageRepository = conversationMessageRepository;
        this.sourceRepository = sourceRepository;
        this.chatClient = chatClient;
        this.retrievalAugmentationAdvisor = retrievalAugmentationAdvisor;
        this.conversationHistoryProvider = conversationHistoryProvider;
    }

    public List<ConversationMessageResponse> listByConversation(UUID conversationId, UUID ownerId) {
        conversationRepository.findByIdAndNotebookOwnerId(conversationId, ownerId)
                .orElseThrow(ConversationNotFoundException::new);
        return conversationMessageRepository.findAllByConversationIdAndOwnerId(conversationId, ownerId).stream()
                .map(ConversationMessageResponse::from)
                .toList();
    }

    public void sendMessage(UUID conversationId, UUID ownerId, String content, SseEmitter emitter) {
        Conversation conversation = conversationRepository.findByIdAndNotebookOwnerId(conversationId, ownerId)
                .orElseThrow(ConversationNotFoundException::new);

        List<Message> promptMessages = conversationHistoryProvider.buildPromptMessages(conversationId, SYSTEM_PROMPT, content);
        String filterExpression = buildActiveSourcesFilter(conversation);

        conversationMessageRepository.save(new ConversationMessage(conversation, MessageRole.user, content));

        MessageStream stream = new MessageStream(emitter, conversation);
        emitter.onCompletion(stream::cancel);
        emitter.onError(error -> stream.cancel());
        emitter.onTimeout(() -> {
            stream.cancel();
            emitter.complete();
        });

        stream.subscription.update(chatClient.prompt()
                .messages(promptMessages)
                .advisors(a -> a.advisors(retrievalAugmentationAdvisor)
                        .param(VectorStoreDocumentRetriever.FILTER_EXPRESSION, filterExpression))
                .stream()
                .content()
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe(
                        stream::onToken,
                        stream::onError,
                        stream::onComplete
                ));
    }

    String buildActiveSourcesFilter(Conversation conversation) {
        Set<Source> activeSources = conversation.getActiveSources();
        List<UUID> sourceIds = activeSources.isEmpty()
                ? sourceRepository.findByNotebookIdAndStatus(conversation.getNotebook().getId(), SourceStatus.READY)
                        .stream().map(Source::getId).toList()
                : activeSources.stream().map(Source::getId).toList();

        if (sourceIds.isEmpty()) {
            return "source_id == 'none'";
        }
        String ids = sourceIds.stream().map(id -> "'" + id + "'").collect(Collectors.joining(","));
        return "source_id in [" + ids + "]";
    }

    private final class MessageStream {
        private final SseEmitter emitter;
        private final Conversation conversation;
        private final StringBuilder assistantResponse = new StringBuilder();
        // A disposed swap also disposes a subscription assigned after a disconnect.
        private final Disposable.Swap subscription = Disposables.swap();
        private final AtomicBoolean closed = new AtomicBoolean();

        private MessageStream(SseEmitter emitter, Conversation conversation) {
            this.emitter = emitter;
            this.conversation = conversation;
        }

        private void cancel() {
            closed.set(true);
            subscription.dispose();
        }

        private void onToken(String chunk) {
            if (closed.get()) return;
            try {
                emitter.send(SseEmitter.event().data(Map.of("token", chunk)));
                assistantResponse.append(chunk);
            } catch (IOException | IllegalStateException error) {
                // A failed transport cannot carry an error event either.
                cancel();
                emitter.completeWithError(error);
            }
        }

        private void onComplete() {
            if (!closed.compareAndSet(false, true)) return;
            ConversationMessage assistantMessage;
            try {
                assistantMessage = conversationMessageRepository.save(
                        new ConversationMessage(conversation, MessageRole.assistant, assistantResponse.toString()));
            } catch (RuntimeException error) {
                sendError(error);
                return;
            }
            try {
                emitter.send(SseEmitter.event().data(Map.of("done", true, "messageId", assistantMessage.getId())));
                emitter.complete();
            } catch (IOException | IllegalStateException error) {
                emitter.completeWithError(error);
            } finally {
                subscription.dispose();
            }
        }

        private void onError(Throwable error) {
            if (!closed.compareAndSet(false, true)) return;
            sendError(error);
        }

        private void sendError(Throwable error) {
            try {
                emitter.send(SseEmitter.event().data(Map.of("error", "STREAM_ERROR")));
            } catch (IOException | IllegalStateException ignored) {
                // Completion can race with this final notification.
            } finally {
                subscription.dispose();
                emitter.completeWithError(error);
            }
        }
    }
}
