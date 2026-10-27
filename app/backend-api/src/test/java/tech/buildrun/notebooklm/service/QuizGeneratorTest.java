package tech.buildrun.notebooklm.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import tech.buildrun.notebooklm.dto.QuizOutput;
import tech.buildrun.notebooklm.dto.QuizQuestion;
import tech.buildrun.notebooklm.entity.Conversation;
import tech.buildrun.notebooklm.entity.Notebook;
import tech.buildrun.notebooklm.entity.Quiz;
import tech.buildrun.notebooklm.entity.QuizStatus;
import tech.buildrun.notebooklm.entity.User;
import tech.buildrun.notebooklm.repository.ChunkMetadataRepository;
import tech.buildrun.notebooklm.repository.QuizRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QuizGeneratorTest {

    @Mock
    private QuizRepository quizRepository;
    @Mock
    private ChunkMetadataRepository chunkMetadataRepository;
    @Mock
    private ChunkEnrichmentService chunkEnrichmentService;
    @Mock
    private VectorStore vectorStore;
    @Mock
    private ChatClient chatClient;

    private QuizGenerator newGenerator() {
        return spy(new QuizGenerator(quizRepository, chunkMetadataRepository, chunkEnrichmentService, vectorStore,
                chatClient));
    }

    private static QuizOutput validOutput() {
        return new QuizOutput(IntStream.range(0, 5)
                .mapToObj(i -> new QuizQuestion("Q" + i, List.of("a", "b", "c", "d"), 0, "porque " + i))
                .toList());
    }

    private Quiz stubQuiz(UUID quizId) {
        User owner = new User("sub", "owner@test.com", "Owner");
        Quiz quiz = new Quiz(new Conversation(new Notebook(owner, "N", null)));
        when(quizRepository.findById(quizId)).thenReturn(Optional.of(quiz));
        return quiz;
    }

    @Test
    void shuffleOptionsKeepsCorrectAnswerPointingToSameText() {
        QuizOutput output = new QuizOutput(IntStream.range(0, 50)
                .mapToObj(i -> new QuizQuestion("Q", List.of("certa", "x", "y", "z"), 0, "e"))
                .toList());

        List<QuizQuestion> shuffled = QuizGenerator.shuffleOptions(output);

        assertThat(shuffled).allSatisfy(q -> assertThat(q.options().get(q.correctIndex())).isEqualTo("certa"));
        assertThat(shuffled).anyMatch(q -> q.correctIndex() != 0);
    }

    @Test
    void validateRejectsWrongQuestionCountAndBadIndex() {
        assertThatThrownBy(() -> QuizGenerator.validate(new QuizOutput(validOutput().questions().subList(0, 4))))
                .isInstanceOf(IllegalStateException.class);

        List<QuizQuestion> bad = new java.util.ArrayList<>(validOutput().questions());
        bad.set(0, new QuizQuestion("Q", List.of("a", "b", "c", "d"), 4, "e"));
        assertThatThrownBy(() -> QuizGenerator.validate(new QuizOutput(bad))).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void generateValidRetriesOnceAndReturnsSecondAttempt() {
        QuizGenerator generator = newGenerator();
        QuizOutput invalid = new QuizOutput(validOutput().questions().subList(0, 4));
        QuizOutput valid = validOutput();
        doReturn(invalid).doReturn(valid).when(generator).callModel(anyString());

        assertThat(generator.generateValid("p")).isSameAs(valid);
    }

    @Test
    void generateValidFailsAfterTwoInvalidAttempts() {
        QuizGenerator generator = newGenerator();
        doReturn(new QuizOutput(List.of())).when(generator).callModel(anyString());

        assertThatThrownBy(() -> generator.generateValid("p")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void generateCompletesQuizAfterBackfillingChunksWithoutKeywords() {
        UUID quizId = UUID.randomUUID();
        UUID sourceId = UUID.randomUUID();
        Quiz quiz = stubQuiz(quizId);
        QuizGenerator generator = newGenerator();

        Document pending = Document.builder().id(UUID.randomUUID().toString()).text("texto").build();
        Document enriched = Document.builder().id(pending.getId()).text("texto")
                .metadata("excerpt_keywords", "k1, k2").metadata("section_summary", "resumo").build();
        when(chunkMetadataRepository.findWithoutKeywords(List.of(sourceId))).thenReturn(List.of(pending));
        when(chunkEnrichmentService.enrich(anyList())).thenReturn(List.of(enriched));
        when(chunkMetadataRepository.topKeywords(List.of(sourceId), 5)).thenReturn(List.of("k1"));
        when(vectorStore.similaritySearch(any(org.springframework.ai.vectorstore.SearchRequest.class)))
                .thenReturn(List.of(enriched));
        doReturn(validOutput()).when(generator).callModel(anyString());

        generator.generate(quizId, List.of(sourceId));

        verify(chunkMetadataRepository).updateEnrichment(pending.getId(), "k1, k2", "resumo");
        assertThat(quiz.getStatus()).isEqualTo(QuizStatus.COMPLETED);
        assertThat(quiz.getResult()).hasSize(5);
    }

    @Test
    void generateSkipsEnrichmentWhenNothingToBackfill() {
        UUID quizId = UUID.randomUUID();
        UUID sourceId = UUID.randomUUID();
        stubQuiz(quizId);
        when(chunkMetadataRepository.findWithoutKeywords(List.of(sourceId))).thenReturn(List.of());
        when(chunkMetadataRepository.topKeywords(List.of(sourceId), 5)).thenReturn(List.of());

        newGenerator().generate(quizId, List.of(sourceId));

        verify(chunkEnrichmentService, never()).enrich(anyList());
    }

    @Test
    void generateFailsQuizWhenSourcesHaveNoContent() {
        UUID quizId = UUID.randomUUID();
        UUID sourceId = UUID.randomUUID();
        Quiz quiz = stubQuiz(quizId);
        when(chunkMetadataRepository.findWithoutKeywords(List.of(sourceId))).thenReturn(List.of());
        when(chunkMetadataRepository.topKeywords(List.of(sourceId), 5)).thenReturn(List.of());

        newGenerator().generate(quizId, List.of(sourceId));

        assertThat(quiz.getStatus()).isEqualTo(QuizStatus.FAILED);
        assertThat(quiz.getErrorMessage()).contains("Sem conteudo");
    }

    @Test
    void generateFailsQuizWhenEnrichmentThrows() {
        UUID quizId = UUID.randomUUID();
        UUID sourceId = UUID.randomUUID();
        Quiz quiz = stubQuiz(quizId);
        when(chunkMetadataRepository.findWithoutKeywords(List.of(sourceId)))
                .thenReturn(List.of(Document.builder().id(UUID.randomUUID().toString()).text("t").build()));
        when(chunkEnrichmentService.enrich(anyList())).thenThrow(new IllegalStateException("llm down"));

        newGenerator().generate(quizId, List.of(sourceId));

        assertThat(quiz.getStatus()).isEqualTo(QuizStatus.FAILED);
        assertThat(quiz.getErrorMessage()).isEqualTo("llm down");
    }
}
