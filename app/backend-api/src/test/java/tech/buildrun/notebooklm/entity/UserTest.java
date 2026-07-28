package tech.buildrun.notebooklm.entity;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class UserTest {

    @Test
    void exposesConstructorValuesThroughGetters() {
        var user = new User("cognito-sub", "user@test.com", "Test User");

        assertThat(user.getCognitoSub()).isEqualTo("cognito-sub");
        assertThat(user.getEmail()).isEqualTo("user@test.com");
        assertThat(user.getName()).isEqualTo("Test User");
    }

    @Test
    void exposesGeneratedIdAndCreatedAt() {
        var user = new User("cognito-sub", "user@test.com", "Test User");
        var id = UUID.randomUUID();
        var createdAt = Instant.now();

        ReflectionTestUtils.setField(user, "id", id);
        ReflectionTestUtils.setField(user, "createdAt", createdAt);

        assertThat(user.getId()).isEqualTo(id);
        assertThat(user.getCreatedAt()).isEqualTo(createdAt);
    }
}
