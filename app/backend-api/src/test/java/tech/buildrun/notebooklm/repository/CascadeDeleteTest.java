package tech.buildrun.notebooklm.repository;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import tech.buildrun.notebooklm.AbstractIntegrationTest;
import tech.buildrun.notebooklm.entity.Conversation;
import tech.buildrun.notebooklm.entity.ConversationMessage;
import tech.buildrun.notebooklm.entity.MessageRole;
import tech.buildrun.notebooklm.entity.Notebook;
import tech.buildrun.notebooklm.entity.Source;
import tech.buildrun.notebooklm.entity.SourceChunk;
import tech.buildrun.notebooklm.entity.SourceType;
import tech.buildrun.notebooklm.entity.User;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
class CascadeDeleteTest extends AbstractIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SourceChunkRepository sourceChunkRepository;

    @Autowired
    private ConversationMessageRepository conversationMessageRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void deletingUserCascadesDownToSourceChunksAndConversationMessages() {
        var user = new User(UUID.randomUUID().toString(), "user@test.com", "Test User");
        entityManager.persist(user);

        var notebook = new Notebook(user, "Notebook", null);
        entityManager.persist(notebook);

        var source = new Source(notebook, "doc.pdf", SourceType.FILE, "some/key", null);
        entityManager.persist(source);

        var chunk = new SourceChunk(source, "content", new float[1536], 0, "text-embedding-ada-002");
        entityManager.persist(chunk);

        var conversation = new Conversation(notebook);
        entityManager.persist(conversation);

        var message = new ConversationMessage(conversation, MessageRole.user, "hi");
        entityManager.persist(message);

        entityManager.flush();

        var userId = user.getId();
        var chunkId = chunk.getId();
        var messageId = message.getId();

        entityManager.clear();

        userRepository.deleteById(userId);
        entityManager.flush();
        entityManager.clear();

        assertThat(sourceChunkRepository.findById(chunkId)).isEmpty();
        assertThat(conversationMessageRepository.findById(messageId)).isEmpty();
        assertThat(userRepository.findById(userId)).isEmpty();
    }
}
