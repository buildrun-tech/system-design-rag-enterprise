package tech.buildrun.notebooklm.core.domain.exception;

public class QuizNotFoundException extends RuntimeException {

    public QuizNotFoundException() {
        super("Quiz nao encontrado");
    }
}
