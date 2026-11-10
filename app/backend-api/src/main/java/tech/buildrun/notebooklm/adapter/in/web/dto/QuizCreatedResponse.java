package tech.buildrun.notebooklm.adapter.in.web.dto;

import tech.buildrun.notebooklm.core.domain.quiz.QuizStatus;

import java.util.UUID;

public record QuizCreatedResponse(UUID quizId, QuizStatus status) {
}
