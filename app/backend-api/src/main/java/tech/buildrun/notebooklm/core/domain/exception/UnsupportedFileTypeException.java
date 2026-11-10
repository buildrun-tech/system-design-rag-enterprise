package tech.buildrun.notebooklm.core.domain.exception;

public class UnsupportedFileTypeException extends RuntimeException {

    public UnsupportedFileTypeException(String filename) {
        super("Formato de arquivo nao suportado: " + filename);
    }
}
