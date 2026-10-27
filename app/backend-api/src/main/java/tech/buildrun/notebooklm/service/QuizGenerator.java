package tech.buildrun.notebooklm.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.document.Document;
import org.springframework.ai.openai.OpenAiChatModel.ResponseFormat;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;
import tech.buildrun.notebooklm.dto.QuizOutput;
import tech.buildrun.notebooklm.dto.QuizQuestion;
import tech.buildrun.notebooklm.entity.Quiz;
import tech.buildrun.notebooklm.repository.ChunkMetadataRepository;
import tech.buildrun.notebooklm.repository.QuizRepository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/** Worker do job de quiz: backfill de metadata, seleção de tópicos por keywords e geração estruturada. */
@Service
public class QuizGenerator {

    static final int QUESTION_COUNT = 5;
    static final int OPTION_COUNT = 4;

    private static final Logger log = LoggerFactory.getLogger(QuizGenerator.class);
    private static final int TOPIC_COUNT = 5;
    private static final int CHUNKS_PER_TOPIC = 3;
    private static final int BACKFILL_BATCH_SIZE = 20;
    private static final int MAX_ATTEMPTS = 2;

    private static final String PROMPT = """
            Você gera quizzes de estudo. Com base SOMENTE nos trechos abaixo, crie exatamente %d perguntas de múltipla escolha.
            Cada pergunta tem exatamente %d opções, apenas 1 correta (correctIndex de 0 a %d) e uma explanation que justifica
            a resposta correta com base nos trechos. Distribua as perguntas entre os tópicos. Escreva no idioma dos trechos.

            Tópicos: %s

            Trechos:
            %s
            """;

    private final QuizRepository quizRepository;
    private final ChunkMetadataRepository chunkMetadataRepository;
    private final ChunkEnrichmentService chunkEnrichmentService;
    private final VectorStore vectorStore;
    private final ChatClient chatClient;

    public QuizGenerator(QuizRepository quizRepository, ChunkMetadataRepository chunkMetadataRepository,
                         ChunkEnrichmentService chunkEnrichmentService, VectorStore vectorStore,
                         ChatClient chatClient) {
        this.quizRepository = quizRepository;
        this.chunkMetadataRepository = chunkMetadataRepository;
        this.chunkEnrichmentService = chunkEnrichmentService;
        this.vectorStore = vectorStore;
        this.chatClient = chatClient;
    }

    public void generate(UUID quizId, List<UUID> sourceIds) {
        long start = System.nanoTime();
        log.info("Quiz generation in: quizId={} sources={}", quizId, sourceIds.size());
        update(quizId, Quiz::markProcessing);
        try {
            backfill(sourceIds);
            List<String> topics = chunkMetadataRepository.topKeywords(sourceIds, TOPIC_COUNT);
            if (topics.isEmpty()) {
                throw new IllegalStateException("Sem conteudo nas sources para gerar o quiz");
            }
            List<QuizQuestion> questions = shuffleOptions(generateValid(buildPrompt(topics, sourceIds)));
            update(quizId, quiz -> quiz.complete(questions));
            log.info("Quiz generation out: quizId={} durationMs={}", quizId, (System.nanoTime() - start) / 1_000_000);
        } catch (Exception e) {
            log.error("Quiz generation failed: quizId={}", quizId, e);
            update(quizId, quiz -> quiz.fail(e.getMessage() == null ? "Falha ao gerar o quiz" : e.getMessage()));
        }
    }

    // Em lotes: se um lote falhar, os anteriores já ficaram persistidos.
    void backfill(List<UUID> sourceIds) {
        List<Document> pending = chunkMetadataRepository.findWithoutKeywords(sourceIds);
        if (pending.isEmpty()) return;
        log.info("Quiz backfill: chunks={}", pending.size());
        for (int from = 0; from < pending.size(); from += BACKFILL_BATCH_SIZE) {
            List<Document> batch = pending.subList(from, Math.min(from + BACKFILL_BATCH_SIZE, pending.size()));
            for (Document chunk : chunkEnrichmentService.enrich(new ArrayList<>(batch))) {
                chunkMetadataRepository.updateEnrichment(chunk.getId(),
                        String.valueOf(chunk.getMetadata().get(ChunkEnrichmentService.KEYWORDS_KEY)),
                        String.valueOf(chunk.getMetadata().get(ChunkEnrichmentService.SUMMARY_KEY)));
            }
        }
    }

    String buildPrompt(List<String> topics, List<UUID> sourceIds) {
        String filter = ActiveSources.filterExpression(sourceIds);
        Map<String, String> chunksById = new LinkedHashMap<>();
        for (String topic : topics) {
            vectorStore.similaritySearch(SearchRequest.builder().query(topic).topK(CHUNKS_PER_TOPIC)
                            .filterExpression(filter).build())
                    .forEach(chunk -> chunksById.putIfAbsent(chunk.getId(), chunk.getText()));
        }
        if (chunksById.isEmpty()) {
            throw new IllegalStateException("Sem conteudo nas sources para gerar o quiz");
        }
        return PROMPT.formatted(QUESTION_COUNT, OPTION_COUNT, OPTION_COUNT - 1, String.join(", ", topics),
                String.join("\n---\n", chunksById.values()));
    }

    QuizOutput callModel(String prompt) {
        // strict json_schema + require_parameters: o OpenRouter só roteia para providers que suportam o schema.
        String schema = new BeanOutputConverter<>(QuizOutput.class).getJsonSchema();

        var options = OpenAiChatOptions.builder()
                .responseFormat(ResponseFormat.builder().type(ResponseFormat.Type.JSON_SCHEMA).jsonSchema(schema).build())
                .extraBody(Map.of("provider", Map.of("require_parameters", true)));

        return chatClient.prompt().user(prompt).options(options).call().entity(QuizOutput.class);
    }

    QuizOutput generateValid(String prompt) {
        IllegalStateException last = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                QuizOutput output = callModel(prompt);
                validate(output);
                return output;
            } catch (IllegalStateException e) {
                log.warn("Quiz output invalid: attempt={} reason={}", attempt, e.getMessage());
                last = e;
            }
        }
        throw new IllegalStateException("Saida do modelo invalida: " + last.getMessage(), last);
    }

    static void validate(QuizOutput output) {
        if (output == null || output.questions() == null || output.questions().size() != QUESTION_COUNT) {
            throw new IllegalStateException("esperadas " + QUESTION_COUNT + " perguntas");
        }
        for (QuizQuestion q : output.questions()) {
            if (blank(q.question()) || blank(q.explanation()) || q.options() == null
                    || q.options().size() != OPTION_COUNT || q.options().stream().anyMatch(QuizGenerator::blank)
                    || q.correctIndex() < 0 || q.correctIndex() >= OPTION_COUNT) {
                throw new IllegalStateException("pergunta invalida: " + q.question());
            }
        }
    }

    // LLMs enviesam a posição da resposta correta; embaralha e remapeia correctIndex.
    static List<QuizQuestion> shuffleOptions(QuizOutput output) {
        return output.questions().stream().map(q -> {
            String correct = q.options().get(q.correctIndex());
            List<String> options = new ArrayList<>(q.options());
            Collections.shuffle(options);
            return new QuizQuestion(q.question(), options, options.indexOf(correct), q.explanation());
        }).toList();
    }

    private void update(UUID quizId, Consumer<Quiz> change) {
        quizRepository.findById(quizId).ifPresent(quiz -> {
            change.accept(quiz);
            quizRepository.save(quiz);
        });
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
