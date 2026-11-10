package tech.buildrun.notebooklm.core.port.out.ai;

import tech.buildrun.notebooklm.core.domain.chunk.ChunkText;
import tech.buildrun.notebooklm.core.domain.quiz.QuizQuestion;

import java.util.List;

public interface StructuredGenerationPort {

    List<QuizQuestion> generateQuiz(List<String> topics, List<ChunkText> chunks, int questionCount, int optionCount);
}
