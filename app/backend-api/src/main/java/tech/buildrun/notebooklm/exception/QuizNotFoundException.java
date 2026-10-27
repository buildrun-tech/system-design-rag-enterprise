package tech.buildrun.notebooklm.exception;

public class QuizNotFoundException extends RuntimeException {

    public QuizNotFoundException() {
        super("Quiz nao encontrado");
    }
}
