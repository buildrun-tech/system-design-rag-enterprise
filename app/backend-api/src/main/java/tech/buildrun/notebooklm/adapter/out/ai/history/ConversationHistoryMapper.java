package tech.buildrun.notebooklm.adapter.out.ai.history;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import tech.buildrun.notebooklm.core.domain.conversation.ConversationMessage;

import java.util.ArrayList;
import java.util.List;

public final class ConversationHistoryMapper {

    private ConversationHistoryMapper() {
    }

    public static List<Message> toPromptMessages(String systemPrompt, List<ConversationMessage> history, String question) {
        List<Message> promptMessages = new ArrayList<>();
        promptMessages.add(new SystemMessage(systemPrompt));
        history.forEach(message -> promptMessages.add(toSpringAiMessage(message)));
        promptMessages.add(new UserMessage(question));
        return promptMessages;
    }

    private static Message toSpringAiMessage(ConversationMessage message) {
        return message.isFromUser() ? new UserMessage(message.content()) : new AssistantMessage(message.content());
    }
}
