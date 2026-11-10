package tech.buildrun.notebooklm.adapter.out.ai.history;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import tech.buildrun.notebooklm.core.domain.conversation.ConversationMessage;
import tech.buildrun.notebooklm.core.domain.conversation.MessageRole;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ConversationHistoryMapperTest {

    private static ConversationMessage message(MessageRole role, String content) {
        return ConversationMessage.restore(UUID.randomUUID(), UUID.randomUUID(), role, content, List.of(), null);
    }

    @Test
    void emptyHistoryYieldsOnlySystemAndUserMessages() {
        List<Message> messages = ConversationHistoryMapper.toPromptMessages("system prompt", List.of(), "new question");

        assertThat(messages).hasSize(2);
        assertThat(messages.get(0).getMessageType()).isEqualTo(MessageType.SYSTEM);
        assertThat(messages.get(1).getMessageType()).isEqualTo(MessageType.USER);
        assertThat(messages.get(1).getText()).isEqualTo("new question");
    }

    @Test
    void historyKeepsGivenOrderBetweenSystemAndNewUserMessage() {
        List<Message> messages = ConversationHistoryMapper.toPromptMessages("system prompt",
                List.of(message(MessageRole.user, "previous question"), message(MessageRole.assistant, "previous answer")),
                "new question");

        assertThat(messages).hasSize(4);
        assertThat(messages.get(0).getMessageType()).isEqualTo(MessageType.SYSTEM);
        assertThat(messages.get(1).getMessageType()).isEqualTo(MessageType.USER);
        assertThat(messages.get(1).getText()).isEqualTo("previous question");
        assertThat(messages.get(2).getMessageType()).isEqualTo(MessageType.ASSISTANT);
        assertThat(messages.get(2).getText()).isEqualTo("previous answer");
        assertThat(messages.get(3).getMessageType()).isEqualTo(MessageType.USER);
        assertThat(messages.get(3).getText()).isEqualTo("new question");
    }
}
