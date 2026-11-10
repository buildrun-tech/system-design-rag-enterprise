package tech.buildrun.notebooklm.core.domain.conversation;

import tech.buildrun.notebooklm.core.domain.exception.InvalidSourceIdsException;
import tech.buildrun.notebooklm.core.domain.source.Source;

import java.time.Instant;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class Conversation {

    private final UUID id;
    private final UUID notebookId;
    private final Set<UUID> activeSourceIds;
    private final Instant createdAt;

    private Conversation(UUID id, UUID notebookId, Set<UUID> activeSourceIds, Instant createdAt) {
        this.id = id;
        this.notebookId = notebookId;
        this.activeSourceIds = new HashSet<>(activeSourceIds);
        this.createdAt = createdAt;
    }

    public static Conversation create(UUID notebookId) {
        return new Conversation(null, notebookId, Set.of(), null);
    }

    /** Reconstrução a partir da persistência; uso exclusivo dos adapters de saída. */
    public static Conversation restore(UUID id, UUID notebookId, Set<UUID> activeSourceIds, Instant createdAt) {
        return new Conversation(id, notebookId, activeSourceIds, createdAt);
    }

    public void activate(Set<UUID> sourceIds) {
        activeSourceIds.clear();
        activeSourceIds.addAll(sourceIds);
    }

    public void activateOnly(Set<UUID> requestedIds, Collection<Source> found) {
        boolean allFound = found.size() == requestedIds.size();
        if (!allFound || !found.stream().allMatch(source -> source.belongsTo(notebookId))) {
            throw new InvalidSourceIdsException();
        }
        activate(requestedIds);
    }

    public ConversationMessage append(MessageRole role, String content) {
        return ConversationMessage.create(id, role, content, List.of());
    }

    public ConversationMessage append(MessageRole role, String content, List<CitedSource> sources) {
        return ConversationMessage.create(id, role, content, sources);
    }

    public boolean belongsTo(UUID notebookId) {
        return this.notebookId.equals(notebookId);
    }

    public boolean hasActiveSources() {
        return !activeSourceIds.isEmpty();
    }

    public UUID id() { return id; }
    public UUID notebookId() { return notebookId; }
    public Set<UUID> activeSourceIds() { return Set.copyOf(activeSourceIds); }
    public Instant createdAt() { return createdAt; }
}
