package tech.buildrun.notebooklm.core.domain.exception;

public class InvalidStateTransitionException extends RuntimeException {

    public InvalidStateTransitionException(String entity, Object from, String action) {
        super(entity + " em estado " + from + " nao permite " + action);
    }
}
