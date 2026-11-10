package tech.buildrun.notebooklm.core.domain.exception;

public class StreamAbortedException extends RuntimeException {

    public StreamAbortedException(Throwable cause) {
        super(cause);
    }
}
