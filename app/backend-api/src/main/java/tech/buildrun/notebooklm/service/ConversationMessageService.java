package tech.buildrun.notebooklm.service;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.Disposable;
import reactor.core.Disposables;
import reactor.core.scheduler.Schedulers;
import tech.buildrun.notebooklm.dto.CitedChunk;
import tech.buildrun.notebooklm.dto.CitedSource;
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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import tech.buildrun.notebooklm.tool.DateTimeTools;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

@Service
public class ConversationMessageService {

    private static final Logger log = LoggerFactory.getLogger(ConversationMessageService.class);

    private static final int SNIPPET_MAX_CHARS = 300;

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
                .chatResponse()
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe(
                        stream::onResponse,
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

    List<CitedSource> buildCitedSources(List<Document> documents) {
        Map<UUID, List<CitedChunk>> chunksBySource = new LinkedHashMap<>();
        for (Document document : documents) {
            Object sourceId = document.getMetadata().get("source_id");
            if (sourceId == null) continue;
            Object chunkIndex = document.getMetadata().get("chunk_index");
            chunksBySource.computeIfAbsent(UUID.fromString(sourceId.toString()), id -> new ArrayList<>())
                    .add(new CitedChunk(chunkIndex instanceof Number n ? n.intValue() : 0, snippet(document.getText())));
        }
        if (chunksBySource.isEmpty()) return List.of();

        Map<UUID, String> names = sourceRepository.findAllById(chunksBySource.keySet()).stream()
                .collect(Collectors.toMap(Source::getId, Source::getName));
        return chunksBySource.entrySet().stream()
                .filter(entry -> names.containsKey(entry.getKey()))
                .map(entry -> new CitedSource(entry.getKey(), names.get(entry.getKey()), entry.getValue()))
                .toList();
    }

    static String snippet(String text) {
        if (text == null) return "";
        String normalized = text.strip().replaceAll("\\s+", " ");
        if (normalized.length() <= SNIPPET_MAX_CHARS) return normalized;
        int cut = normalized.lastIndexOf(' ', SNIPPET_MAX_CHARS);
        return normalized.substring(0, cut > 0 ? cut : SNIPPET_MAX_CHARS) + "…";
    }

    private final class MessageStream {
        private final SseEmitter emitter;
        private final Conversation conversation;
        private final StringBuilder assistantResponse = new StringBuilder();
        // A disposed swap also disposes a subscription assigned after a disconnect.
        private final Disposable.Swap subscription = Disposables.swap();
        private final AtomicBoolean closed = new AtomicBoolean();
        // RetrievalAugmentationAdvisor só anexa os docs no chunk com finish reason do stream.
        private volatile List<Document> retrievedDocuments = List.of();
        // callbacks rodam em outra thread: carrega o MDC da request original
        private final Map<String, String> mdc = MDC.getCopyOfContextMap();

        private MessageStream(SseEmitter emitter, Conversation conversation) {
            this.emitter = emitter;
            this.conversation = conversation;
        }

        private void cancel() {
            closed.set(true);
            subscription.dispose();
        }

        private void onResponse(ChatResponse response) {
            if (response.getMetadata().get(RetrievalAugmentationAdvisor.DOCUMENT_CONTEXT) instanceof List<?> docs) {
                retrievedDocuments = docs.stream().filter(Document.class::isInstance).map(Document.class::cast).toList();
            }
            Generation generation = response.getResult();
            String text = generation == null ? null : generation.getOutput().getText();
            if (text != null && !text.isEmpty()) onToken(text);
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
            if (mdc != null) MDC.setContextMap(mdc);
            try {
                complete();
            } finally {
                MDC.clear();
            }
        }

        private void complete() {
            ConversationMessage assistantMessage;
            List<CitedSource> sources;
            try {
                sources = buildCitedSources(retrievedDocuments);
                assistantMessage = conversationMessageRepository.save(
                        new ConversationMessage(conversation, MessageRole.assistant, assistantResponse.toString(), sources));
            } catch (RuntimeException error) {
                sendError(error);
                return;
            }
            try {
                log.info("SSE stream out: conversationId={} messageId={} responseChars={} sources={}",
                        conversation.getId(), assistantMessage.getId(), assistantResponse.length(), sources.size());
                emitter.send(SseEmitter.event().data(
                        Map.of("done", true, "messageId", assistantMessage.getId(), "sources", sources)));
                emitter.complete();
            } catch (IOException | IllegalStateException error) {
                emitter.completeWithError(error);
            } finally {
                subscription.dispose();
            }
        }

        private void onError(Throwable error) {
            if (!closed.compareAndSet(false, true)) return;
            if (mdc != null) MDC.setContextMap(mdc);
            try {
                log.error("SSE stream failed: conversationId={}", conversation.getId(), error);
                sendError(error);
            } finally {
                MDC.clear();
            }
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
