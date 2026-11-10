package tech.buildrun.notebooklm.core.domain.exception;

public class ConversationNotFoundException extends RuntimeException {

    public ConversationNotFoundException() {
        super("Conversa nao encontrada");
    }
}
