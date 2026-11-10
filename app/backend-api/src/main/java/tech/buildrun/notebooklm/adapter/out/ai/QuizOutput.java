package tech.buildrun.notebooklm.adapter.out.ai;

import java.util.List;

public record QuizOutput(List<Question> questions) {

    public record Question(String question, List<String> options, int correctIndex, String explanation) {
    }
}
