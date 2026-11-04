package tech.buildrun.notebooklm.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.rag.generation.augmentation.ContextualQueryAugmenter;
import org.springframework.ai.rag.preretrieval.query.transformation.CompressionQueryTransformer;
import org.springframework.ai.rag.preretrieval.query.transformation.RewriteQueryTransformer;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.ai.support.ToolCallbacks;
import tech.buildrun.notebooklm.tool.DateTimeTools;
import tech.buildrun.notebooklm.tool.EventingToolCallback;

@Configuration
public class ChatClientConfig {

    private static final int RAG_TOP_K = 5;

    @Bean
    public ChatClient chatClient(ChatClient.Builder chatClientBuilder) {
        // tools só aqui: os builders dos transformers (clone) não precisam de tools.
        // EventingToolCallback publica start/end/error de cada tool no listener da request (SSE).
        return chatClientBuilder
                .defaultToolCallbacks(EventingToolCallback.wrap(ToolCallbacks.from(new DateTimeTools())))
                .build();
    }

    @Bean
    public CompressionQueryTransformer compressionQueryTransformer(ChatClient.Builder chatClientBuilder) {
        return CompressionQueryTransformer.builder()
                .chatClientBuilder(chatClientBuilder.clone())
                .build();
    }

    @Bean
    public RewriteQueryTransformer rewriteQueryTransformer(ChatClient.Builder chatClientBuilder) {
        return RewriteQueryTransformer.builder()
                .chatClientBuilder(chatClientBuilder.clone())
                .build();
    }

    @Bean
    public VectorStoreDocumentRetriever vectorStoreDocumentRetriever(VectorStore vectorStore) {
        return VectorStoreDocumentRetriever.builder()
                .vectorStore(vectorStore)
                .topK(RAG_TOP_K)
                .build();
    }

    @Bean
    public RetrievalAugmentationAdvisor retrievalAugmentationAdvisor(
            CompressionQueryTransformer compressionQueryTransformer,
            RewriteQueryTransformer rewriteQueryTransformer,
            VectorStoreDocumentRetriever vectorStoreDocumentRetriever) {

        return RetrievalAugmentationAdvisor.builder()
                .queryTransformers(compressionQueryTransformer, rewriteQueryTransformer)
                .documentRetriever(vectorStoreDocumentRetriever)
                // allowEmptyContext=true: mantém o comportamento atual de responder sem grounding
                // quando nenhum chunk é encontrado, em vez do "fora da base de conhecimento" padrão.
                .queryAugmenter(ContextualQueryAugmenter.builder().allowEmptyContext(true).build())
                .build();
    }
}
