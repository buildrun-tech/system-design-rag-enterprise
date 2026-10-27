package tech.buildrun.notebooklm.dto;

import java.util.List;

public record QuizQuestion(String question, List<String> options, int correctIndex, String explanation) {
}
