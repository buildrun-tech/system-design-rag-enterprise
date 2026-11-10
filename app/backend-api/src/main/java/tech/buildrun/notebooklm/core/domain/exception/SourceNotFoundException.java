package tech.buildrun.notebooklm.core.domain.exception;

public class SourceNotFoundException extends RuntimeException {

    public SourceNotFoundException() {
        super("Source nao encontrada");
    }
}
