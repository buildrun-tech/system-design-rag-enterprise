package tech.buildrun.notebooklm.config;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.rag.preretrieval.query.transformation.CompressionQueryTransformer;
import org.springframework.ai.rag.preretrieval.query.transformation.RewriteQueryTransformer;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
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
    void compressionQueryTransformerBeanIsBuiltFromProvidedChatClientBuilder() {
        ChatModel chatModel = mock(ChatModel.class);
        ChatClient.Builder builder = ChatClient.builder(chatModel);

        CompressionQueryTransformer transformer = new ChatClientConfig().compressionQueryTransformer(builder);

        assertThat(transformer).isNotNull();
    }

    @Test
    void rewriteQueryTransformerBeanIsBuiltFromProvidedChatClientBuilder() {
        ChatModel chatModel = mock(ChatModel.class);
        ChatClient.Builder builder = ChatClient.builder(chatModel);

        RewriteQueryTransformer transformer = new ChatClientConfig().rewriteQueryTransformer(builder);

        assertThat(transformer).isNotNull();
    }

    @Test
    void vectorStoreDocumentRetrieverBeanIsBuiltFromProvidedVectorStore() {
        VectorStore vectorStore = mock(VectorStore.class);

        VectorStoreDocumentRetriever retriever = new ChatClientConfig().vectorStoreDocumentRetriever(vectorStore);

        assertThat(retriever).isNotNull();
    }

    @Test
    void retrievalAugmentationAdvisorBeanComposesTransformersAndRetriever() {
        ChatModel chatModel = mock(ChatModel.class);
        ChatClient.Builder builder = ChatClient.builder(chatModel);
        VectorStore vectorStore = mock(VectorStore.class);
        ChatClientConfig config = new ChatClientConfig();

        CompressionQueryTransformer compressionQueryTransformer = config.compressionQueryTransformer(builder);
        RewriteQueryTransformer rewriteQueryTransformer = config.rewriteQueryTransformer(builder);
        VectorStoreDocumentRetriever documentRetriever = config.vectorStoreDocumentRetriever(vectorStore);

        RetrievalAugmentationAdvisor advisor = config.retrievalAugmentationAdvisor(
                compressionQueryTransformer, rewriteQueryTransformer, documentRetriever);

        assertThat(advisor).isNotNull();
    }
}
