package tech.buildrun.notebooklm.exception;

public class ConversationNotFoundException extends RuntimeException {

    public ConversationNotFoundException() {
        super("Conversa nao encontrada");
    }
}
