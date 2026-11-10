package tech.buildrun.notebooklm.adapter.out.persistence.mapper;

import tech.buildrun.notebooklm.adapter.out.persistence.entity.QuizJpaEntity;
import tech.buildrun.notebooklm.adapter.out.persistence.entity.QuizJpaEntity.QuestionJson;
import tech.buildrun.notebooklm.core.domain.quiz.Quiz;
import tech.buildrun.notebooklm.core.domain.quiz.QuizQuestion;

import java.util.List;

public final class QuizMapper {

    private QuizMapper() {
    }

    public static Quiz toDomain(QuizJpaEntity entity) {
        return Quiz.restore(entity.getId(), entity.getConversationId(), entity.getStatus(),
                toQuestions(entity.getResult()), entity.getErrorMessage(), entity.getCreatedAt(),
                entity.getUpdatedAt());
    }

    public static QuizJpaEntity toEntity(Quiz quiz) {
        QuizJpaEntity entity = new QuizJpaEntity(quiz.conversationId());
        copyState(quiz, entity);
        return entity;
    }

    public static void copyState(Quiz quiz, QuizJpaEntity entity) {
        entity.apply(quiz.status(), toJson(quiz.result()), quiz.errorMessage());
    }

    private static List<QuizQuestion> toQuestions(List<QuestionJson> json) {
        return json == null ? null : json.stream()
                .map(q -> new QuizQuestion(q.question(), q.options(), q.correctIndex(), q.explanation())).toList();
    }

    private static List<QuestionJson> toJson(List<QuizQuestion> questions) {
        return questions == null ? null : questions.stream()
                .map(q -> new QuestionJson(q.question(), q.options(), q.correctIndex(), q.explanation())).toList();
    }
}
