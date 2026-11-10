package tech.buildrun.notebooklm.adapter.out.persistence;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import tech.buildrun.notebooklm.AbstractIntegrationTest;
import tech.buildrun.notebooklm.adapter.out.persistence.entity.ConversationJpaEntity;
import tech.buildrun.notebooklm.adapter.out.persistence.entity.ConversationMessageJpaEntity;
import tech.buildrun.notebooklm.adapter.out.persistence.entity.NotebookJpaEntity;
import tech.buildrun.notebooklm.adapter.out.persistence.entity.SourceJpaEntity;
import tech.buildrun.notebooklm.adapter.out.persistence.entity.UserJpaEntity;
import tech.buildrun.notebooklm.adapter.out.persistence.repository.ConversationMessageJpaRepository;
import tech.buildrun.notebooklm.adapter.out.persistence.repository.UserJpaRepository;
import tech.buildrun.notebooklm.core.domain.conversation.MessageRole;
import tech.buildrun.notebooklm.core.domain.source.SourceStatus;
import tech.buildrun.notebooklm.core.domain.source.SourceType;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
class CascadeDeleteTest extends AbstractIntegrationTest {

    @Autowired
    private UserJpaRepository userRepository;

    @Autowired
    private ConversationMessageJpaRepository conversationMessageRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void deletingUserCascadesDownToConversationMessages() {
        var user = new UserJpaEntity(null, UUID.randomUUID().toString(), "user@test.com", "Test User", null);
        entityManager.persist(user);

        var notebook = new NotebookJpaEntity(null, user.getId(), "Notebook", null, null, null);
        entityManager.persist(notebook);

        var source = new SourceJpaEntity(null, notebook.getId(), "doc.pdf", SourceType.FILE, "some/key", null,
                SourceStatus.PENDING, null, null);
        entityManager.persist(source);

        var conversation = new ConversationJpaEntity(null, notebook.getId(), Set.of(), null);
        entityManager.persist(conversation);

        var message = new ConversationMessageJpaEntity(null, conversation.getId(), MessageRole.user, "hi", List.of(), null);
        entityManager.persist(message);

        entityManager.flush();

        var userId = user.getId();
        var messageId = message.getId();

        entityManager.clear();

        userRepository.deleteById(userId);
        entityManager.flush();
        entityManager.clear();

        assertThat(conversationMessageRepository.findById(messageId)).isEmpty();
        assertThat(userRepository.findById(userId)).isEmpty();
    }
}
