package tech.buildrun.notebooklm.service;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ConversationMessageService {

    private static final String SYSTEM_PROMPT =
            "Voce e um assistente que ajuda o usuario a entender os documentos do notebook.";

    private final ConversationRepository conversationRepository;
    private final ConversationMessageRepository conversationMessageRepository;
    private final SourceRepository sourceRepository;
    private final ChatClient chatClient;
    private final QuestionAnswerAdvisor questionAnswerAdvisor;

    public ConversationMessageService(ConversationRepository conversationRepository,
                                       ConversationMessageRepository conversationMessageRepository,
                                       SourceRepository sourceRepository,
                                       ChatClient chatClient,
                                       QuestionAnswerAdvisor questionAnswerAdvisor) {
        this.conversationRepository = conversationRepository;
        this.conversationMessageRepository = conversationMessageRepository;
        this.sourceRepository = sourceRepository;
        this.chatClient = chatClient;
        this.questionAnswerAdvisor = questionAnswerAdvisor;
    }

    public List<ConversationMessageResponse> listByConversation(UUID conversationId, UUID ownerId) {
        conversationRepository.findByIdAndNotebook_Owner_Id(conversationId, ownerId)
                .orElseThrow(ConversationNotFoundException::new);
        return conversationMessageRepository.findAllByConversationIdAndOwnerId(conversationId, ownerId).stream()
                .map(ConversationMessageResponse::from)
                .toList();
    }

    public void sendMessage(UUID conversationId, UUID ownerId, String content, SseEmitter emitter) {
        Conversation conversation = conversationRepository.findByIdAndNotebook_Owner_Id(conversationId, ownerId)
                .orElseThrow(ConversationNotFoundException::new);

        List<Message> promptMessages = buildPromptMessages(conversationId, content);
        String filterExpression = buildActiveSourcesFilter(conversation);

        conversationMessageRepository.save(new ConversationMessage(conversation, MessageRole.user, content));

        StringBuilder assistantResponse = new StringBuilder();

        chatClient.prompt()
                .messages(promptMessages)
                .advisors(a -> a.advisors(questionAnswerAdvisor)
                        .param(QuestionAnswerAdvisor.FILTER_EXPRESSION, filterExpression))
                .stream()
                .content()
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe(
                        chunk -> onToken(emitter, assistantResponse, chunk),
                        error -> onStreamError(emitter, error),
                        () -> onStreamComplete(emitter, conversation, assistantResponse)
                );
    }

    String buildActiveSourcesFilter(Conversation conversation) {
        Set<Source> activeSources = conversation.getActiveSources();
        List<UUID> sourceIds = activeSources.isEmpty()
                ? sourceRepository.findByNotebook_IdAndStatus(conversation.getNotebook().getId(), SourceStatus.READY)
                        .stream().map(Source::getId).toList()
                : activeSources.stream().map(Source::getId).toList();

        if (sourceIds.isEmpty()) {
            return "source_id == 'none'";
        }
        String ids = sourceIds.stream().map(id -> "'" + id + "'").collect(Collectors.joining(","));
        return "source_id in [" + ids + "]";
    }

    private List<Message> buildPromptMessages(UUID conversationId, String newUserContent) {

        List<ConversationMessage> history = conversationMessageRepository
                .findTop10ByConversation_IdOrderByCreatedAtDesc(conversationId);
        Collections.reverse(history);

        List<Message> promptMessages = new ArrayList<>();
        promptMessages.add(new SystemMessage(SYSTEM_PROMPT));
        history.forEach(message -> promptMessages.add(toSpringAiMessage(message)));
        promptMessages.add(new UserMessage(newUserContent));

        return promptMessages;
    }

    private Message toSpringAiMessage(ConversationMessage message) {
        return message.getRole() == MessageRole.user
                ? new UserMessage(message.getContent())
                : new AssistantMessage(message.getContent());
    }

    private void onToken(SseEmitter emitter, StringBuilder assistantResponse, String chunk) {
        assistantResponse.append(chunk);
        try {
            emitter.send(SseEmitter.event().data(Map.of("token", chunk)));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private void onStreamComplete(SseEmitter emitter, Conversation conversation, StringBuilder assistantResponse) {
        ConversationMessage assistantMessage = conversationMessageRepository.save(
                new ConversationMessage(conversation, MessageRole.assistant, assistantResponse.toString()));
        try {
            emitter.send(SseEmitter.event().data(Map.of("done", true, "messageId", assistantMessage.getId())));
            emitter.complete();
        } catch (IOException e) {
            emitter.completeWithError(e);
        }
    }

    private void onStreamError(SseEmitter emitter, Throwable error) {
        try {
            emitter.send(SseEmitter.event().data(Map.of("error", "STREAM_ERROR")));
        } catch (IOException ignored) {
            // stream already broken, nothing to notify
        }
        emitter.completeWithError(error);
    }
}
