package tech.buildrun.notebooklm.core.port.in;

import tech.buildrun.notebooklm.core.domain.quiz.Quiz;

import java.util.UUID;

public interface GenerateQuizPort {

    Quiz generate(UUID conversationId, UUID ownerId);
}
