package tech.buildrun.notebooklm.adapter.out.ai;

import org.slf4j.MDC;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.stereotype.Component;
import reactor.core.Disposable;
import reactor.core.Disposables;
import reactor.core.scheduler.Schedulers;
import tech.buildrun.notebooklm.adapter.out.ai.history.ConversationHistoryMapper;
import tech.buildrun.notebooklm.adapter.out.ai.tool.EventingToolCallback;
import tech.buildrun.notebooklm.core.domain.conversation.CitedChunk;
import tech.buildrun.notebooklm.core.domain.conversation.CitedSource;
import tech.buildrun.notebooklm.core.domain.conversation.ConversationMessage;
import tech.buildrun.notebooklm.core.domain.conversation.ToolEvent;
import tech.buildrun.notebooklm.core.domain.exception.StreamAbortedException;
import tech.buildrun.notebooklm.core.domain.source.Source;
import tech.buildrun.notebooklm.core.port.out.ai.ChatCompletionPort;
import tech.buildrun.notebooklm.core.port.out.ai.ChatStreamListener;
import tech.buildrun.notebooklm.core.port.out.persistence.SourceRepositoryPort;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@Component
public class OpenAiChatCompletionAdapter implements ChatCompletionPort {

    private static final int SNIPPET_MAX_CHARS = 300;
    private static final String RETRIEVAL_NAME = "retrieval";
    private static final String RETRIEVAL_LABEL = "Buscando nas suas fontes";

    private static final String SYSTEM_PROMPT =
            "Voce e um assistente que ajuda o usuario a entender os documentos do notebook.";

    private final ChatClient chatClient;
    private final RetrievalAugmentationAdvisor retrievalAugmentationAdvisor;
    private final SourceRepositoryPort sourceRepository;

    public OpenAiChatCompletionAdapter(ChatClient chatClient, RetrievalAugmentationAdvisor retrievalAugmentationAdvisor,
                                       SourceRepositoryPort sourceRepository) {
        this.chatClient = chatClient;
        this.retrievalAugmentationAdvisor = retrievalAugmentationAdvisor;
        this.sourceRepository = sourceRepository;
    }

    @Override
    public void stream(List<ConversationMessage> history, String question, List<UUID> sourceIds,
                       ChatStreamListener listener) {
        List<Message> promptMessages = ConversationHistoryMapper.toPromptMessages(SYSTEM_PROMPT, history, question);
        String filterExpression = filterExpression(sourceIds);

        Stream stream = new Stream(listener);
        listener.onStart(stream::cancel);
        stream.startRetrieval();

        stream.subscription.update(chatClient.prompt()
                .messages(promptMessages)
                .advisors(a -> a.advisors(retrievalAugmentationAdvisor)
                        .param(VectorStoreDocumentRetriever.FILTER_EXPRESSION, filterExpression))
                .toolContext(Map.of(EventingToolCallback.LISTENER_KEY, (Consumer<ToolEvent>) stream::onToolEvent))
                .stream()
                .chatResponse()
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe(stream::onResponse, stream::onError, stream::onComplete));
    }

    static String retrievalSummary(List<Document> documents) {
        if (documents.isEmpty()) return "Nenhum trecho relevante encontrado";
        long sourceCount = documents.stream().map(d -> d.getMetadata().get("source_id")).filter(Objects::nonNull)
                .distinct().count();
        int chunkCount = documents.size();
        return chunkCount + (chunkCount == 1 ? " trecho encontrado em " : " trechos encontrados em ")
                + sourceCount + (sourceCount == 1 ? " fonte" : " fontes");
    }

    static String filterExpression(List<UUID> sourceIds) {
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
                .collect(Collectors.toMap(Source::id, Source::name));
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

    private final class Stream {
        private final ChatStreamListener listener;
        private final Disposable.Swap subscription = Disposables.swap();
        private final AtomicBoolean closed = new AtomicBoolean();
        private volatile List<Document> retrievedDocuments = List.of();
        private final Map<String, String> mdc = MDC.getCopyOfContextMap();
        private final String retrievalId = UUID.randomUUID().toString();
        private final AtomicBoolean retrievalEnded = new AtomicBoolean();
        private long retrievalStartedAt;

        private Stream(ChatStreamListener listener) {
            this.listener = listener;
        }

        private void cancel() {
            closed.set(true);
            subscription.dispose();
        }

        private void startRetrieval() {
            retrievalStartedAt = System.nanoTime();
            onToolEvent(new ToolEvent(retrievalId, RETRIEVAL_NAME, RETRIEVAL_LABEL, ToolEvent.Status.start,
                    null, null, null, null));
        }

        /** O fim é o 1º chunk do modelo, então a duração inclui a latência do 1º token; medir só a busca exigiria envolver o DocumentRetriever. */
        private void endRetrieval() {
            if (!retrievalEnded.compareAndSet(false, true)) return;
            onToolEvent(new ToolEvent(retrievalId, RETRIEVAL_NAME, RETRIEVAL_LABEL, ToolEvent.Status.end,
                    null, null, (System.nanoTime() - retrievalStartedAt) / 1_000_000,
                    retrievalSummary(retrievedDocuments)));
        }

        private void onResponse(ChatResponse response) {
            if (response.getMetadata().get(RetrievalAugmentationAdvisor.DOCUMENT_CONTEXT) instanceof List<?> docs) {
                retrievedDocuments = docs.stream().filter(Document.class::isInstance).map(Document.class::cast).toList();
            }
            endRetrieval();
            Generation generation = response.getResult();
            String text = generation == null ? null : generation.getOutput().getText();
            if (text != null && !text.isEmpty()) onToken(text);
        }

        private void onToken(String token) {
            if (closed.get()) return;
            try {
                listener.onToken(token);
            } catch (StreamAbortedException aborted) {
                cancel();
            }
        }

        private void onToolEvent(ToolEvent event) {
            if (closed.get()) return;
            listener.onToolCall(event);
        }

        private void onComplete() {
            if (!closed.compareAndSet(false, true)) return;
            withMdc(() -> {
                try {
                    List<CitedSource> sources;
                    try {
                        sources = buildCitedSources(retrievedDocuments);
                    } catch (RuntimeException error) {
                        listener.onError(error);
                        return;
                    }
                    listener.onComplete(sources);
                } finally {
                    subscription.dispose();
                }
            });
        }

        private void onError(Throwable error) {
            if (!closed.compareAndSet(false, true)) return;
            withMdc(() -> {
                try {
                    listener.onError(error);
                } finally {
                    subscription.dispose();
                }
            });
        }

        private void withMdc(Runnable action) {
            if (mdc != null) MDC.setContextMap(mdc);
            try {
                action.run();
            } finally {
                MDC.clear();
            }
        }
    }
}
