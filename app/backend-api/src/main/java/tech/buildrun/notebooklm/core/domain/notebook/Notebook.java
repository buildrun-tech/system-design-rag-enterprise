package tech.buildrun.notebooklm.core.domain.notebook;

import java.time.Instant;
import java.util.UUID;

public class Notebook {

    private final UUID id;
    private final UUID ownerId;
    private String name;
    private String description;
    private final Instant createdAt;
    private final Instant updatedAt;

    private Notebook(UUID id, UUID ownerId, String name, String description, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.ownerId = ownerId;
        this.name = name;
        this.description = description;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static Notebook create(UUID ownerId, String name, String description) {
        return new Notebook(null, ownerId, name, description, null, null);
    }

    /** Reconstrução a partir da persistência; uso exclusivo dos adapters de saída. */
    public static Notebook restore(UUID id, UUID ownerId, String name, String description,
                                   Instant createdAt, Instant updatedAt) {
        return new Notebook(id, ownerId, name, description, createdAt, updatedAt);
    }

    public void rename(String name) {
        this.name = name;
    }

    public void describe(String description) {
        this.description = description;
    }

    public void patch(String name, String description) {
        if (name != null) {
            rename(name);
        }
        if (description != null) {
            describe(description);
        }
    }

    public boolean isOwnedBy(UUID userId) {
        return ownerId.equals(userId);
    }

    public UUID id() { return id; }
    public UUID ownerId() { return ownerId; }
    public String name() { return name; }
    public String description() { return description; }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }
}
