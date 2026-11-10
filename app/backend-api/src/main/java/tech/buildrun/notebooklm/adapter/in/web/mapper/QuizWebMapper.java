package tech.buildrun.notebooklm.adapter.in.web.mapper;

import tech.buildrun.notebooklm.adapter.in.web.dto.QuizCreatedResponse;
import tech.buildrun.notebooklm.adapter.in.web.dto.QuizResponse;
import tech.buildrun.notebooklm.adapter.in.web.dto.QuizResponse.QuizQuestionResponse;
import tech.buildrun.notebooklm.core.domain.quiz.Quiz;

public final class QuizWebMapper {

    private QuizWebMapper() {
    }

    public static QuizCreatedResponse toCreated(Quiz quiz) {
        return new QuizCreatedResponse(quiz.id(), quiz.status());
    }

    public static QuizResponse toResponse(Quiz quiz) {
        return new QuizResponse(quiz.id(), quiz.status(),
                quiz.result() == null ? null : quiz.result().stream()
                        .map(q -> new QuizQuestionResponse(q.question(), q.options(), q.correctIndex(),
                                q.explanation())).toList(),
                quiz.errorMessage());
    }
}
