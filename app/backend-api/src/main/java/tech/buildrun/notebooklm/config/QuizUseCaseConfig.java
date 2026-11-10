package tech.buildrun.notebooklm.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tech.buildrun.notebooklm.core.port.in.GenerateQuizPort;
import tech.buildrun.notebooklm.core.port.in.GetQuizPort;
import tech.buildrun.notebooklm.core.port.out.ai.ChunkEnrichmentPort;
import tech.buildrun.notebooklm.core.port.out.ai.StructuredGenerationPort;
import tech.buildrun.notebooklm.core.port.out.async.BackgroundTaskPort;
import tech.buildrun.notebooklm.core.port.out.persistence.ConversationRepositoryPort;
import tech.buildrun.notebooklm.core.port.out.persistence.QuizRepositoryPort;
import tech.buildrun.notebooklm.core.port.out.persistence.SourceRepositoryPort;
import tech.buildrun.notebooklm.core.port.out.vector.ChunkInsightsPort;
import tech.buildrun.notebooklm.core.usecase.GenerateQuizUseCase;
import tech.buildrun.notebooklm.core.usecase.GetQuizUseCase;

@Configuration
public class QuizUseCaseConfig {

    @Bean
    public GenerateQuizPort generateQuizPort(ConversationRepositoryPort conversationRepository,
                                             SourceRepositoryPort sourceRepository, QuizRepositoryPort quizRepository,
                                             ChunkInsightsPort chunkInsights, ChunkEnrichmentPort chunkEnrichment,
                                             StructuredGenerationPort generation, BackgroundTaskPort background) {
        return new GenerateQuizUseCase(conversationRepository, sourceRepository, quizRepository, chunkInsights,
                chunkEnrichment, generation, background);
    }

    @Bean
    public GetQuizPort getQuizPort(ConversationRepositoryPort conversationRepository,
                                   QuizRepositoryPort quizRepository) {
        return new GetQuizUseCase(conversationRepository, quizRepository);
    }
}
