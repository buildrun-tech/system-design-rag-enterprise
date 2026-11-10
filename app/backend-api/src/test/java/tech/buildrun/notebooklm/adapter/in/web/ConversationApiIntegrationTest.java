package tech.buildrun.notebooklm.adapter.in.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import tech.buildrun.notebooklm.AbstractIntegrationTest;
import tech.buildrun.notebooklm.core.domain.notebook.Notebook;
import tech.buildrun.notebooklm.core.domain.source.Source;
import tech.buildrun.notebooklm.core.domain.source.SourceStatus;
import tech.buildrun.notebooklm.core.domain.source.SourceType;
import tech.buildrun.notebooklm.core.domain.user.User;
import tech.buildrun.notebooklm.core.port.out.persistence.NotebookRepositoryPort;
import tech.buildrun.notebooklm.core.port.out.persistence.SourceRepositoryPort;
import tech.buildrun.notebooklm.core.port.out.persistence.UserRepositoryPort;
import tech.buildrun.notebooklm.security.TestJwtDecoderConfig;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestJwtDecoderConfig.class)
class ConversationApiIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepositoryPort userRepository;

    @Autowired
    private NotebookRepositoryPort notebookRepository;

    @Autowired
    private SourceRepositoryPort sourceRepository;

    @Test
    void createsConversationUsingAllReadySourcesByDefault() throws Exception {
        String sub = "owner-" + UUID.randomUUID();
        User owner = userRepository.save(User.create(sub, sub + "@test.com", "Owner"));
        Notebook notebook = notebookRepository.save(Notebook.create(owner.id(), "Notebook", null));
        Source source = sourceRepository.save(Source.create(notebook.id(), "doc.pdf", SourceType.FILE, "key", null));
        source.markProcessing();
        source.markReady();
        source = sourceRepository.save(source);

        mockMvc.perform(post("/api/v1/notebooks/" + notebook.id() + "/conversations")
                        .header("Authorization", "Bearer " + sub)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.notebookId").value(notebook.id().toString()))
                .andExpect(jsonPath("$.activeSourceIds[0]").value(source.id().toString()));
    }

    @Test
    void createRejectsSourceIdNotBelongingToNotebook() throws Exception {
        String sub = "owner-" + UUID.randomUUID();
        User owner = userRepository.save(User.create(sub, sub + "@test.com", "Owner"));
        Notebook notebook = notebookRepository.save(Notebook.create(owner.id(), "Notebook", null));

        String foreignSourceId = UUID.randomUUID().toString();

        mockMvc.perform(post("/api/v1/notebooks/" + notebook.id() + "/conversations")
                        .header("Authorization", "Bearer " + sub)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"activeSourceIds\":[\"" + foreignSourceId + "\"]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_SOURCE_IDS"));
    }

    @Test
    void createFailsForNotebookOfAnotherUser() throws Exception {
        String ownerSub = "owner-" + UUID.randomUUID();
        String intruderSub = "intruder-" + UUID.randomUUID();
        User owner = userRepository.save(User.create(ownerSub, ownerSub + "@test.com", "Owner"));
        Notebook notebook = notebookRepository.save(Notebook.create(owner.id(), "Notebook", null));

        mockMvc.perform(post("/api/v1/notebooks/" + notebook.id() + "/conversations")
                        .header("Authorization", "Bearer " + intruderSub)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("NOTEBOOK_NOT_FOUND"));
    }

    @Test
    void listReturnsConversationsOrderedByCreatedAtDesc() throws Exception {
        String sub = "owner-" + UUID.randomUUID();
        User owner = userRepository.save(User.create(sub, sub + "@test.com", "Owner"));
        Notebook notebook = notebookRepository.save(Notebook.create(owner.id(), "Notebook", null));

        mockMvc.perform(post("/api/v1/notebooks/" + notebook.id() + "/conversations")
                        .header("Authorization", "Bearer " + sub)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/notebooks/" + notebook.id() + "/conversations")
                        .header("Authorization", "Bearer " + sub))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].notebookId").value(notebook.id().toString()));
    }
}
