package tech.buildrun.notebooklm.core.domain.exception;

public class NotebookNotFoundException extends RuntimeException {

    public NotebookNotFoundException() {
        super("Notebook nao encontrado");
    }
}
