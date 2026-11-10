package tech.buildrun.notebooklm.core.domain.quiz;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public record QuizQuestion(String question, List<String> options, int correctIndex, String explanation) {

    public boolean isWellFormed(int optionCount) {
        return !blank(question) && !blank(explanation) && options != null && options.size() == optionCount
                && options.stream().noneMatch(QuizQuestion::blank) && correctIndex >= 0 && correctIndex < optionCount;
    }

    public QuizQuestion withShuffledOptions() {
        String correct = options.get(correctIndex);
        List<String> shuffled = new ArrayList<>(options);
        Collections.shuffle(shuffled);
        return new QuizQuestion(question, shuffled, shuffled.indexOf(correct), explanation);
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
