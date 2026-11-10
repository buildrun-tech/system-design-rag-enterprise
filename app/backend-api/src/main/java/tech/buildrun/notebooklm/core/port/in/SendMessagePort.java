package tech.buildrun.notebooklm.core.port.in;

import java.util.UUID;

public interface SendMessagePort {

    void send(UUID conversationId, UUID ownerId, String content, MessageStreamListener listener);
}
