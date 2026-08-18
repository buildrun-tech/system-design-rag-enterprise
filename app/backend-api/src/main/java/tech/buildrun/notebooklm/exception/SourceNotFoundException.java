package tech.buildrun.notebooklm.exception;

public class SourceNotFoundException extends RuntimeException {

    public SourceNotFoundException() {
        super("Source nao encontrada");
    }
}
