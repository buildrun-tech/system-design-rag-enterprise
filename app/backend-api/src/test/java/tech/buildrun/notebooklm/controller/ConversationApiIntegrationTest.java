package tech.buildrun.notebooklm.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import tech.buildrun.notebooklm.AbstractIntegrationTest;
import tech.buildrun.notebooklm.entity.Notebook;
import tech.buildrun.notebooklm.entity.Source;
import tech.buildrun.notebooklm.entity.SourceStatus;
import tech.buildrun.notebooklm.entity.SourceType;
import tech.buildrun.notebooklm.entity.User;
import tech.buildrun.notebooklm.repository.NotebookRepository;
import tech.buildrun.notebooklm.repository.SourceRepository;
import tech.buildrun.notebooklm.repository.UserRepository;
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
    private UserRepository userRepository;

    @Autowired
    private NotebookRepository notebookRepository;

    @Autowired
    private SourceRepository sourceRepository;

    @Test
    void createsConversationUsingAllReadySourcesByDefault() throws Exception {
        String sub = "owner-" + UUID.randomUUID();
        User owner = userRepository.save(new User(sub, sub + "@test.com", "Owner"));
        Notebook notebook = notebookRepository.save(new Notebook(owner, "Notebook", null));
        Source source = sourceRepository.save(new Source(notebook, "doc.pdf", SourceType.FILE, "key", null));
        source.setStatus(SourceStatus.READY);
        sourceRepository.save(source);

        mockMvc.perform(post("/api/v1/notebooks/" + notebook.getId() + "/conversations")
                        .header("Authorization", "Bearer " + sub)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.notebookId").value(notebook.getId().toString()))
                .andExpect(jsonPath("$.activeSourceIds[0]").value(source.getId().toString()));
    }

    @Test
    void createRejectsSourceIdNotBelongingToNotebook() throws Exception {
        String sub = "owner-" + UUID.randomUUID();
        User owner = userRepository.save(new User(sub, sub + "@test.com", "Owner"));
        Notebook notebook = notebookRepository.save(new Notebook(owner, "Notebook", null));

        String foreignSourceId = UUID.randomUUID().toString();

        mockMvc.perform(post("/api/v1/notebooks/" + notebook.getId() + "/conversations")
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
        User owner = userRepository.save(new User(ownerSub, ownerSub + "@test.com", "Owner"));
        Notebook notebook = notebookRepository.save(new Notebook(owner, "Notebook", null));

        mockMvc.perform(post("/api/v1/notebooks/" + notebook.getId() + "/conversations")
                        .header("Authorization", "Bearer " + intruderSub)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("NOTEBOOK_NOT_FOUND"));
    }

    @Test
    void listReturnsConversationsOrderedByCreatedAtDesc() throws Exception {
        String sub = "owner-" + UUID.randomUUID();
        User owner = userRepository.save(new User(sub, sub + "@test.com", "Owner"));
        Notebook notebook = notebookRepository.save(new Notebook(owner, "Notebook", null));

        mockMvc.perform(post("/api/v1/notebooks/" + notebook.getId() + "/conversations")
                        .header("Authorization", "Bearer " + sub)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/notebooks/" + notebook.getId() + "/conversations")
                        .header("Authorization", "Bearer " + sub))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].notebookId").value(notebook.getId().toString()));
    }
}
