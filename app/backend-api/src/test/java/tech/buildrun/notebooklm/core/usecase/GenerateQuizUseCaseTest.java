package tech.buildrun.notebooklm.core.usecase;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tech.buildrun.notebooklm.core.domain.chunk.ChunkText;
import tech.buildrun.notebooklm.core.domain.chunk.EnrichedChunk;
import tech.buildrun.notebooklm.core.domain.conversation.Conversation;
import tech.buildrun.notebooklm.core.domain.exception.ConversationNotFoundException;
import tech.buildrun.notebooklm.core.domain.quiz.Quiz;
import tech.buildrun.notebooklm.core.domain.quiz.QuizQuestion;
import tech.buildrun.notebooklm.core.domain.quiz.QuizStatus;
import tech.buildrun.notebooklm.core.domain.source.Source;
import tech.buildrun.notebooklm.core.domain.source.SourceStatus;
import tech.buildrun.notebooklm.core.domain.source.SourceType;
import tech.buildrun.notebooklm.core.port.out.ai.ChunkEnrichmentPort;
import tech.buildrun.notebooklm.core.port.out.ai.StructuredGenerationPort;
import tech.buildrun.notebooklm.core.port.out.persistence.ConversationRepositoryPort;
import tech.buildrun.notebooklm.core.port.out.persistence.QuizRepositoryPort;
import tech.buildrun.notebooklm.core.port.out.persistence.SourceRepositoryPort;
import tech.buildrun.notebooklm.core.port.out.vector.ChunkInsightsPort;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GenerateQuizUseCaseTest {

    @Mock
    private ConversationRepositoryPort conversationRepository;
    @Mock
    private SourceRepositoryPort sourceRepository;
    @Mock
    private ChunkInsightsPort chunkInsights;
    @Mock
    private ChunkEnrichmentPort chunkEnrichment;
    @Mock
    private StructuredGenerationPort generation;
    @Mock
    private QuizRepositoryPort quizRepository;

    private final UUID ownerId = UUID.randomUUID();
    private final UUID notebookId = UUID.randomUUID();
    private final UUID conversationId = UUID.randomUUID();
    private final UUID quizId = UUID.randomUUID();
    private final UUID sourceId = UUID.randomUUID();
    private Quiz stored;
    private GenerateQuizUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new GenerateQuizUseCase(conversationRepository, sourceRepository, quizRepository, chunkInsights,
                chunkEnrichment, generation, Runnable::run);
    }

    private void stubQuizStore() {
        when(quizRepository.save(any(Quiz.class))).thenAnswer(invocation -> {
            Quiz quiz = invocation.getArgument(0);
            if (quiz.id() == null) {
                stored = Quiz.restore(quizId, quiz.conversationId(), quiz.status(), quiz.result(),
                        quiz.errorMessage(), null, null);
                return stored;
            }
            stored = quiz;
            return quiz;
        });
        when(quizRepository.findById(quizId)).thenAnswer(invocation -> Optional.of(stored));
    }

    private void stubConversation(Set<UUID> activeSourceIds) {
        when(conversationRepository.findByIdAndOwnerId(conversationId, ownerId))
                .thenReturn(Optional.of(Conversation.restore(conversationId, notebookId, activeSourceIds, null)));
    }

    private static List<QuizQuestion> validQuestions() {
        return IntStream.range(0, 5)
                .mapToObj(i -> new QuizQuestion("Q" + i, List.of("a", "b", "c", "d"), 0, "porque " + i)).toList();
    }

    private void stubHappyPath() {
        when(chunkInsights.findWithoutKeywords(List.of(sourceId))).thenReturn(List.of());
        when(chunkInsights.topKeywords(List.of(sourceId), 5)).thenReturn(List.of("k1"));
        when(chunkInsights.searchByTopics(List.of(sourceId), List.of("k1"), 3))
                .thenReturn(List.of(new ChunkText("c1", "texto")));
    }

    @Test
    void generateCompletesQuizUsingActiveSources() {
        stubQuizStore();
        stubConversation(Set.of(sourceId));
        stubHappyPath();
        when(generation.generateQuiz(anyList(), anyList(), anyInt(), anyInt())).thenReturn(validQuestions());

        Quiz created = useCase.generate(conversationId, ownerId);

        assertThat(created.id()).isEqualTo(quizId);
        assertThat(stored.status()).isEqualTo(QuizStatus.COMPLETED);
        assertThat(stored.result()).hasSize(5)
                .allSatisfy(q -> assertThat(q.options().get(q.correctIndex())).isEqualTo("a"));
    }

    @Test
    void generateFallsBackToReadySourcesOfNotebookWhenNoneActive() {
        stubQuizStore();
        stubConversation(Set.of());
        Source ready = Source.restore(sourceId, notebookId, "d", SourceType.FILE, "k", null, SourceStatus.READY,
                null, null);
        when(sourceRepository.findByNotebookIdAndStatus(notebookId, SourceStatus.READY)).thenReturn(List.of(ready));
        stubHappyPath();
        when(generation.generateQuiz(anyList(), anyList(), anyInt(), anyInt())).thenReturn(validQuestions());

        useCase.generate(conversationId, ownerId);

        assertThat(stored.status()).isEqualTo(QuizStatus.COMPLETED);
    }

    @Test
    void generateThrowsWhenConversationNotOwned() {
        when(conversationRepository.findByIdAndOwnerId(conversationId, ownerId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.generate(conversationId, ownerId))
                .isInstanceOf(ConversationNotFoundException.class);
        verify(quizRepository, never()).save(any());
    }

    @Test
    void backfillsChunksWithoutKeywordsInBatches() {
        stubQuizStore();
        stubConversation(Set.of(sourceId));
        List<ChunkText> pending = IntStream.range(0, 25).mapToObj(i -> new ChunkText("id" + i, "t")).toList();
        when(chunkInsights.findWithoutKeywords(List.of(sourceId))).thenReturn(pending);
        when(chunkEnrichment.enrich(anyList())).thenAnswer(invocation -> {
            List<ChunkText> batch = invocation.getArgument(0);
            return batch.stream().map(c -> new EnrichedChunk(c.id(), "k1, k2", "resumo")).toList();
        });
        when(chunkInsights.topKeywords(List.of(sourceId), 5)).thenReturn(List.of("k1"));
        when(chunkInsights.searchByTopics(List.of(sourceId), List.of("k1"), 3))
                .thenReturn(List.of(new ChunkText("c1", "texto")));
        when(generation.generateQuiz(anyList(), anyList(), anyInt(), anyInt())).thenReturn(validQuestions());

        useCase.generate(conversationId, ownerId);

        verify(chunkEnrichment, times(2)).enrich(anyList());
        verify(chunkInsights, times(25)).updateEnrichment(any(EnrichedChunk.class));
        assertThat(stored.status()).isEqualTo(QuizStatus.COMPLETED);
    }

    @Test
    void retriesOnceOnInvalidOutput() {
        stubQuizStore();
        stubConversation(Set.of(sourceId));
        stubHappyPath();
        when(generation.generateQuiz(anyList(), anyList(), anyInt(), anyInt()))
                .thenReturn(validQuestions().subList(0, 4)).thenReturn(validQuestions());

        useCase.generate(conversationId, ownerId);

        assertThat(stored.status()).isEqualTo(QuizStatus.COMPLETED);
    }

    @Test
    void failsAfterTwoInvalidAttempts() {
        stubQuizStore();
        stubConversation(Set.of(sourceId));
        stubHappyPath();
        List<QuizQuestion> bad = new ArrayList<>(validQuestions());
        bad.set(0, new QuizQuestion("Q", List.of("a", "b", "c", "d"), 4, "e"));
        when(generation.generateQuiz(anyList(), anyList(), anyInt(), anyInt())).thenReturn(bad);

        useCase.generate(conversationId, ownerId);

        assertThat(stored.status()).isEqualTo(QuizStatus.FAILED);
        assertThat(stored.errorMessage()).startsWith("Saida do modelo invalida");
    }

    @Test
    void failsQuizWhenSourcesHaveNoContent() {
        stubQuizStore();
        stubConversation(Set.of(sourceId));
        when(chunkInsights.findWithoutKeywords(List.of(sourceId))).thenReturn(List.of());
        when(chunkInsights.topKeywords(List.of(sourceId), 5)).thenReturn(List.of());

        useCase.generate(conversationId, ownerId);

        assertThat(stored.status()).isEqualTo(QuizStatus.FAILED);
        assertThat(stored.errorMessage()).contains("Sem conteudo");
        verify(chunkEnrichment, never()).enrich(anyList());
    }

    @Test
    void failsQuizWhenEnrichmentThrows() {
        stubQuizStore();
        stubConversation(Set.of(sourceId));
        when(chunkInsights.findWithoutKeywords(List.of(sourceId))).thenReturn(List.of(new ChunkText("c", "t")));
        when(chunkEnrichment.enrich(anyList())).thenThrow(new IllegalStateException("llm down"));

        useCase.generate(conversationId, ownerId);

        assertThat(stored.status()).isEqualTo(QuizStatus.FAILED);
        assertThat(stored.errorMessage()).isEqualTo("llm down");
    }
}
