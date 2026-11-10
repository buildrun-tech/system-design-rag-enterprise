package tech.buildrun.notebooklm.adapter.in.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import tech.buildrun.notebooklm.core.domain.quiz.QuizStatus;

import java.util.List;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record QuizResponse(UUID quizId, QuizStatus status, List<QuizQuestionResponse> questions, String errorMessage) {

    public record QuizQuestionResponse(String question, List<String> options, int correctIndex, String explanation) {
    }
}
