package tech.buildrun.notebooklm.core.domain.conversation;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public class ConversationMessage {

    private static final int PREVIEW_MAX_LENGTH = 50;

    private final UUID id;
    private final UUID conversationId;
    private final MessageRole role;
    private final String content;
    private final List<CitedSource> sources;
    private final Instant createdAt;

    private ConversationMessage(UUID id, UUID conversationId, MessageRole role, String content,
                                List<CitedSource> sources, Instant createdAt) {
        this.id = id;
        this.conversationId = conversationId;
        this.role = role;
        this.content = content;
        this.sources = sources == null ? List.of() : List.copyOf(sources);
        this.createdAt = createdAt;
    }

    static ConversationMessage create(UUID conversationId, MessageRole role, String content,
                                      List<CitedSource> sources) {
        return new ConversationMessage(null, conversationId, role, content, sources, null);
    }

    /** Reconstrução a partir da persistência; uso exclusivo dos adapters de saída. */
    public static ConversationMessage restore(UUID id, UUID conversationId, MessageRole role, String content,
                                              List<CitedSource> sources, Instant createdAt) {
        return new ConversationMessage(id, conversationId, role, content, sources, createdAt);
    }

    public String preview() {
        if (content.length() <= PREVIEW_MAX_LENGTH) {
            return content;
        }
        return content.substring(0, PREVIEW_MAX_LENGTH) + "...";
    }

    public boolean isFromUser() {
        return role == MessageRole.user;
    }

    public UUID id() { return id; }
    public UUID conversationId() { return conversationId; }
    public MessageRole role() { return role; }
    public String content() { return content; }
    public List<CitedSource> sources() { return sources; }
    public Instant createdAt() { return createdAt; }
}
