package tech.buildrun.notebooklm.dto;

import tech.buildrun.notebooklm.entity.QuizStatus;

import java.util.UUID;

public record QuizCreatedResponse(UUID quizId, QuizStatus status) {
}
