package tech.buildrun.notebooklm.exception;

public class NotebookNotFoundException extends RuntimeException {

    public NotebookNotFoundException() {
        super("Notebook nao encontrado");
    }
}
