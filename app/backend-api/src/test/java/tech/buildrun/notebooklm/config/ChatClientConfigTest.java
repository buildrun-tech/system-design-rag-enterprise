package tech.buildrun.notebooklm.config;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.vectorstore.VectorStore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class ChatClientConfigTest {

    @Test
    void chatClientBeanIsBuiltFromProvidedChatModel() {
        ChatModel chatModel = mock(ChatModel.class);
        ChatClient.Builder builder = ChatClient.builder(chatModel);

        ChatClient chatClient = new ChatClientConfig().chatClient(builder);

        assertThat(chatClient).isNotNull();
    }

    @Test
    void questionAnswerAdvisorBeanIsBuiltFromProvidedVectorStore() {
        VectorStore vectorStore = mock(VectorStore.class);

        QuestionAnswerAdvisor advisor = new ChatClientConfig().questionAnswerAdvisor(vectorStore);

        assertThat(advisor).isNotNull();
    }
}
