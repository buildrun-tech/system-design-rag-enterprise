package tech.buildrun.notebooklm.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import tech.buildrun.notebooklm.entity.QuizStatus;

import java.util.List;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record QuizResponse(UUID quizId, QuizStatus status, List<QuizQuestion> questions, String errorMessage) {
}
