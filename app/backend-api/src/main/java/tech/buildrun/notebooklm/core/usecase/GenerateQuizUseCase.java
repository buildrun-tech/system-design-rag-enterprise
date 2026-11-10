package tech.buildrun.notebooklm.core.usecase;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tech.buildrun.notebooklm.core.domain.chunk.ChunkText;
import tech.buildrun.notebooklm.core.domain.conversation.Conversation;
import tech.buildrun.notebooklm.core.domain.exception.ConversationNotFoundException;
import tech.buildrun.notebooklm.core.domain.quiz.Quiz;
import tech.buildrun.notebooklm.core.domain.quiz.QuizQuestion;
import tech.buildrun.notebooklm.core.domain.source.Source;
import tech.buildrun.notebooklm.core.domain.source.SourceStatus;
import tech.buildrun.notebooklm.core.port.in.GenerateQuizPort;
import tech.buildrun.notebooklm.core.port.out.ai.ChunkEnrichmentPort;
import tech.buildrun.notebooklm.core.port.out.ai.StructuredGenerationPort;
import tech.buildrun.notebooklm.core.port.out.async.BackgroundTaskPort;
import tech.buildrun.notebooklm.core.port.out.persistence.ConversationRepositoryPort;
import tech.buildrun.notebooklm.core.port.out.persistence.QuizRepositoryPort;
import tech.buildrun.notebooklm.core.port.out.persistence.SourceRepositoryPort;
import tech.buildrun.notebooklm.core.port.out.vector.ChunkInsightsPort;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

public class GenerateQuizUseCase implements GenerateQuizPort {

    static final int QUESTION_COUNT = 5;
    static final int OPTION_COUNT = 4;

    private static final Logger log = LoggerFactory.getLogger(GenerateQuizUseCase.class);
    private static final int TOPIC_COUNT = 5;
    private static final int CHUNKS_PER_TOPIC = 3;
    private static final int BACKFILL_BATCH_SIZE = 20;
    private static final int MAX_ATTEMPTS = 2;
    private static final String NO_CONTENT = "Sem conteudo nas sources para gerar o quiz";

    private final ConversationRepositoryPort conversationRepository;
    private final SourceRepositoryPort sourceRepository;
    private final QuizRepositoryPort quizRepository;
    private final ChunkInsightsPort chunkInsights;
    private final ChunkEnrichmentPort chunkEnrichment;
    private final StructuredGenerationPort generation;
    private final BackgroundTaskPort background;

    public GenerateQuizUseCase(ConversationRepositoryPort conversationRepository,
                               SourceRepositoryPort sourceRepository, QuizRepositoryPort quizRepository,
                               ChunkInsightsPort chunkInsights, ChunkEnrichmentPort chunkEnrichment,
                               StructuredGenerationPort generation, BackgroundTaskPort background) {
        this.conversationRepository = conversationRepository;
        this.sourceRepository = sourceRepository;
        this.quizRepository = quizRepository;
        this.chunkInsights = chunkInsights;
        this.chunkEnrichment = chunkEnrichment;
        this.generation = generation;
        this.background = background;
    }

    @Override
    public Quiz generate(UUID conversationId, UUID ownerId) {
        Conversation conversation = conversationRepository.findByIdAndOwnerId(conversationId, ownerId)
                .orElseThrow(ConversationNotFoundException::new);
        List<UUID> sourceIds = activeSourceIds(conversation);
        Quiz quiz = quizRepository.save(Quiz.create(conversationId));
        UUID quizId = quiz.id();
        background.submit(() -> produce(quizId, sourceIds));
        return quiz;
    }

    private List<UUID> activeSourceIds(Conversation conversation) {
        if (conversation.hasActiveSources()) {
            return List.copyOf(conversation.activeSourceIds());
        }
        return sourceRepository.findByNotebookIdAndStatus(conversation.notebookId(), SourceStatus.READY)
                .stream().map(Source::id).toList();
    }

    void produce(UUID quizId, List<UUID> sourceIds) {
        long start = System.nanoTime();
        log.info("Quiz generation in: quizId={} sources={}", quizId, sourceIds.size());
        update(quizId, Quiz::markProcessing);
        try {
            backfill(sourceIds);
            List<String> topics = chunkInsights.topKeywords(sourceIds, TOPIC_COUNT);
            if (topics.isEmpty()) {
                throw new IllegalStateException(NO_CONTENT);
            }
            List<ChunkText> chunks = chunkInsights.searchByTopics(sourceIds, topics, CHUNKS_PER_TOPIC);
            if (chunks.isEmpty()) {
                throw new IllegalStateException(NO_CONTENT);
            }
            List<QuizQuestion> questions = generateValid(topics, chunks).stream()
                    .map(QuizQuestion::withShuffledOptions).toList();
            update(quizId, quiz -> quiz.complete(questions));
            log.info("Quiz generation out: quizId={} durationMs={}", quizId, (System.nanoTime() - start) / 1_000_000);
        } catch (Exception e) {
            log.error("Quiz generation failed: quizId={}", quizId, e);
            update(quizId, quiz -> quiz.fail(e.getMessage() == null ? "Falha ao gerar o quiz" : e.getMessage()));
        }
    }

    private void backfill(List<UUID> sourceIds) {
        List<ChunkText> pending = chunkInsights.findWithoutKeywords(sourceIds);
        if (pending.isEmpty()) return;
        log.info("Quiz backfill: chunks={}", pending.size());
        for (int from = 0; from < pending.size(); from += BACKFILL_BATCH_SIZE) {
            List<ChunkText> batch = pending.subList(from, Math.min(from + BACKFILL_BATCH_SIZE, pending.size()));
            chunkEnrichment.enrich(new ArrayList<>(batch)).forEach(chunkInsights::updateEnrichment);
        }
    }

    private List<QuizQuestion> generateValid(List<String> topics, List<ChunkText> chunks) {
        IllegalStateException last = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                List<QuizQuestion> questions = generation.generateQuiz(topics, chunks, QUESTION_COUNT, OPTION_COUNT);
                validate(questions);
                return questions;
            } catch (IllegalStateException e) {
                log.warn("Quiz output invalid: attempt={} reason={}", attempt, e.getMessage());
                last = e;
            }
        }
        throw new IllegalStateException("Saida do modelo invalida: " + last.getMessage(), last);
    }

    private static void validate(List<QuizQuestion> questions) {
        if (questions == null || questions.size() != QUESTION_COUNT) {
            throw new IllegalStateException("esperadas " + QUESTION_COUNT + " perguntas");
        }
        for (QuizQuestion q : questions) {
            if (!q.isWellFormed(OPTION_COUNT)) {
                throw new IllegalStateException("pergunta invalida: " + q.question());
            }
        }
    }

    private void update(UUID quizId, Consumer<Quiz> change) {
        quizRepository.findById(quizId).ifPresent(quiz -> {
            change.accept(quiz);
            quizRepository.save(quiz);
        });
    }
}
