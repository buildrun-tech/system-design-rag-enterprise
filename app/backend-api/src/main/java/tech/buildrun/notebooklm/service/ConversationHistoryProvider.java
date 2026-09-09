package tech.buildrun.notebooklm.service;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Component;
import tech.buildrun.notebooklm.entity.ConversationMessage;
import tech.buildrun.notebooklm.entity.MessageRole;
import tech.buildrun.notebooklm.repository.ConversationMessageRepository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Component
public class ConversationHistoryProvider {

    private final ConversationMessageRepository conversationMessageRepository;

    public ConversationHistoryProvider(ConversationMessageRepository conversationMessageRepository) {
        this.conversationMessageRepository = conversationMessageRepository;
    }

    public List<Message> buildPromptMessages(UUID conversationId, String systemPrompt, String newUserContent) {
        List<ConversationMessage> history = conversationMessageRepository
                .findTop10ByConversationIdOrderByCreatedAtDesc(conversationId);
        Collections.reverse(history);

        List<Message> promptMessages = new ArrayList<>();
        promptMessages.add(new SystemMessage(systemPrompt));
        history.forEach(message -> promptMessages.add(toSpringAiMessage(message)));
        promptMessages.add(new UserMessage(newUserContent));

        return promptMessages;
    }

    private Message toSpringAiMessage(ConversationMessage message) {
        return message.getRole() == MessageRole.user
                ? new UserMessage(message.getContent())
                : new AssistantMessage(message.getContent());
    }
}
