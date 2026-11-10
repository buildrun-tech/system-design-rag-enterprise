package tech.buildrun.notebooklm.core.domain.exception;

public class InvalidSourceIdsException extends RuntimeException {

    public InvalidSourceIdsException() {
        super("Uma ou mais sources informadas nao pertencem ao notebook");
    }
}
