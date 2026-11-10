package tech.buildrun.notebooklm.core.domain.user;

import java.time.Instant;
import java.util.UUID;

public class User {

    private final UUID id;
    private String cognitoSub;
    private final String email;
    private final String name;
    private final Instant createdAt;

    private User(UUID id, String cognitoSub, String email, String name, Instant createdAt) {
        this.id = id;
        this.cognitoSub = cognitoSub;
        this.email = email;
        this.name = name;
        this.createdAt = createdAt;
    }

    public static User create(String cognitoSub, String email, String name) {
        return new User(null, cognitoSub, email.toLowerCase(), name, null);
    }

    /** Reconstrução a partir da persistência; uso exclusivo dos adapters de saída. */
    public static User restore(UUID id, String cognitoSub, String email, String name, Instant createdAt) {
        return new User(id, cognitoSub, email, name, createdAt);
    }

    public void reattachTo(String cognitoSub) {
        this.cognitoSub = cognitoSub;
    }

    public UUID id() { return id; }
    public String cognitoSub() { return cognitoSub; }
    public String email() { return email; }
    public String name() { return name; }
    public Instant createdAt() { return createdAt; }
}
