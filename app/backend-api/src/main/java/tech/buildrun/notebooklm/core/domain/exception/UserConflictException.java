package tech.buildrun.notebooklm.core.domain.exception;

public class UserConflictException extends RuntimeException {

    public UserConflictException(Throwable cause) {
        super("Usuario ja existe", cause);
    }
}
