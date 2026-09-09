package tech.buildrun.notebooklm.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.test.util.ReflectionTestUtils;
import tech.buildrun.notebooklm.entity.Conversation;
import tech.buildrun.notebooklm.entity.ConversationMessage;
import tech.buildrun.notebooklm.entity.MessageRole;
import tech.buildrun.notebooklm.entity.Notebook;
import tech.buildrun.notebooklm.entity.User;
import tech.buildrun.notebooklm.repository.ConversationMessageRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConversationHistoryProviderTest {

    @Mock
    private ConversationMessageRepository conversationMessageRepository;

    @Test
    void buildPromptMessagesWithEmptyHistoryReturnsOnlySystemAndUserMessages() {
        UUID conversationId = UUID.randomUUID();
        when(conversationMessageRepository.findTop10ByConversationIdOrderByCreatedAtDesc(conversationId))
                .thenReturn(new ArrayList<>());

        ConversationHistoryProvider provider = new ConversationHistoryProvider(conversationMessageRepository);

        List<Message> messages = provider.buildPromptMessages(conversationId, "system prompt", "new question");

        assertThat(messages).hasSize(2);
        assertThat(messages.get(0).getMessageType()).isEqualTo(MessageType.SYSTEM);
        assertThat(messages.get(1).getMessageType()).isEqualTo(MessageType.USER);
        assertThat(messages.get(1).getText()).isEqualTo("new question");
    }

    @Test
    void buildPromptMessagesOrdersHistoryChronologicallyBetweenSystemAndNewUserMessage() {
        User owner = new User("sub", "owner@test.com", "Owner");
        Notebook notebook = new Notebook(owner, "Notebook", null);
        Conversation conversation = new Conversation(notebook);
        UUID conversationId = UUID.randomUUID();
        ReflectionTestUtils.setField(conversation, "id", conversationId);

        ConversationMessage previousUserMessage = new ConversationMessage(conversation, MessageRole.user, "previous question");
        ConversationMessage previousAssistantMessage = new ConversationMessage(conversation, MessageRole.assistant, "previous answer");

        // repository retorna desc (mais recente primeiro), provider deve reverter pra ordem cronológica
        when(conversationMessageRepository.findTop10ByConversationIdOrderByCreatedAtDesc(conversationId))
                .thenReturn(new ArrayList<>(List.of(previousAssistantMessage, previousUserMessage)));

        ConversationHistoryProvider provider = new ConversationHistoryProvider(conversationMessageRepository);

        List<Message> messages = provider.buildPromptMessages(conversationId, "system prompt", "new question");

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
