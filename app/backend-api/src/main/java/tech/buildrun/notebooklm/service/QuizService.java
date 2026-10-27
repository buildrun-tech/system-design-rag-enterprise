package tech.buildrun.notebooklm.service;

import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import tech.buildrun.notebooklm.dto.QuizCreatedResponse;
import tech.buildrun.notebooklm.dto.QuizResponse;
import tech.buildrun.notebooklm.entity.Conversation;
import tech.buildrun.notebooklm.entity.Quiz;
import tech.buildrun.notebooklm.entity.QuizStatus;
import tech.buildrun.notebooklm.exception.ConversationNotFoundException;
import tech.buildrun.notebooklm.exception.QuizNotFoundException;
import tech.buildrun.notebooklm.repository.ConversationRepository;
import tech.buildrun.notebooklm.repository.QuizRepository;
import tech.buildrun.notebooklm.repository.SourceRepository;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

@Service
public class QuizService {

    static final Duration STALE_AFTER = Duration.ofMinutes(10);

    private final ConversationRepository conversationRepository;
    private final SourceRepository sourceRepository;
    private final QuizRepository quizRepository;
    private final QuizGenerator quizGenerator;
    private final Executor executor;

    @Autowired
    public QuizService(ConversationRepository conversationRepository, SourceRepository sourceRepository,
                       QuizRepository quizRepository, QuizGenerator quizGenerator) {
        this(conversationRepository, sourceRepository, quizRepository, quizGenerator,
                Executors.newVirtualThreadPerTaskExecutor());
    }

    QuizService(ConversationRepository conversationRepository, SourceRepository sourceRepository,
                QuizRepository quizRepository, QuizGenerator quizGenerator, Executor executor) {
        this.conversationRepository = conversationRepository;
        this.sourceRepository = sourceRepository;
        this.quizRepository = quizRepository;
        this.quizGenerator = quizGenerator;
        this.executor = executor;
    }

    public QuizCreatedResponse create(UUID conversationId, UUID ownerId) {
        Conversation conversation = conversationRepository.findByIdAndNotebookOwnerId(conversationId, ownerId)
                .orElseThrow(ConversationNotFoundException::new);
        // resolvido na thread da request (lazy collection); o worker só recebe ids
        List<UUID> sourceIds = ActiveSources.resolveIds(conversation, sourceRepository);
        Quiz quiz = quizRepository.save(new Quiz(conversation));

        Map<String, String> mdc = MDC.getCopyOfContextMap();
        executor.execute(() -> {
            if (mdc != null) MDC.setContextMap(mdc);
            try {
                quizGenerator.generate(quiz.getId(), sourceIds);
            } finally {
                MDC.clear();
            }
        });
        return new QuizCreatedResponse(quiz.getId(), quiz.getStatus());
    }

    public QuizResponse get(UUID conversationId, UUID quizId, UUID ownerId) {
        conversationRepository.findByIdAndNotebookOwnerId(conversationId, ownerId)
                .orElseThrow(ConversationNotFoundException::new);
        Quiz quiz = quizRepository.findByIdAndConversationId(quizId, conversationId)
                .orElseThrow(QuizNotFoundException::new);

        // job órfão (instância caiu no meio): vira FAILED na leitura, sem scheduler
        boolean inFlight = quiz.getStatus() == QuizStatus.PENDING || quiz.getStatus() == QuizStatus.PROCESSING;
        if (inFlight && quiz.getUpdatedAt().isBefore(Instant.now().minus(STALE_AFTER))) {
            return new QuizResponse(quiz.getId(), QuizStatus.FAILED, null, "Tempo excedido ao gerar o quiz");
        }
        return new QuizResponse(quiz.getId(), quiz.getStatus(), quiz.getResult(), quiz.getErrorMessage());
    }
}
