package tech.buildrun.notebooklm.adapter.in.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import tech.buildrun.notebooklm.AbstractIntegrationTest;
import tech.buildrun.notebooklm.adapter.out.persistence.repository.NotebookJpaRepository;
import tech.buildrun.notebooklm.adapter.out.persistence.repository.UserJpaRepository;
import tech.buildrun.notebooklm.core.domain.notebook.Notebook;
import tech.buildrun.notebooklm.core.domain.source.Source;
import tech.buildrun.notebooklm.core.domain.source.SourceType;
import tech.buildrun.notebooklm.core.domain.user.User;
import tech.buildrun.notebooklm.core.port.out.persistence.NotebookRepositoryPort;
import tech.buildrun.notebooklm.core.port.out.persistence.SourceRepositoryPort;
import tech.buildrun.notebooklm.core.port.out.persistence.UserRepositoryPort;
import tech.buildrun.notebooklm.security.TestJwtDecoderConfig;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestJwtDecoderConfig.class)
class NotebookApiIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepositoryPort userRepository;

    @Autowired
    private NotebookRepositoryPort notebookRepository;

    @Autowired
    private SourceRepositoryPort sourceRepository;

    @Autowired
    private NotebookJpaRepository notebookJpaRepository;

    @Autowired
    private UserJpaRepository userJpaRepository;

    @Test
    void ownerReadsOwnNotebookWithSources() throws Exception {
        String sub = "owner-" + UUID.randomUUID();
        User owner = userRepository.save(User.create(sub, sub + "@test.com", "Owner"));
        Notebook notebook = notebookRepository.save(Notebook.create(owner.id(), "Notebook", "desc"));
        Source source = sourceRepository.save(Source.create(notebook.id(), "doc.pdf", SourceType.FILE, "key", null));

        mockMvc.perform(get("/api/v1/notebooks/" + notebook.id())
                        .header("Authorization", "Bearer " + sub))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Notebook"))
                .andExpect(jsonPath("$.sources[0].id").value(source.id().toString()))
                .andExpect(jsonPath("$.sources[0].name").value("doc.pdf"));
    }

    @Test
    void ownerUpdatesOwnNotebookPartially() throws Exception {
        String sub = "owner-" + UUID.randomUUID();
        User owner = userRepository.save(User.create(sub, sub + "@test.com", "Owner"));
        Notebook notebook = notebookRepository.save(Notebook.create(owner.id(), "Notebook", "desc"));

        mockMvc.perform(patch("/api/v1/notebooks/" + notebook.id())
                        .header("Authorization", "Bearer " + sub)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Renamed\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Renamed"))
                .andExpect(jsonPath("$.description").value("desc"));
    }

    @Test
    void ownerDeletesOwnNotebook() throws Exception {
        String sub = "owner-" + UUID.randomUUID();
        User owner = userRepository.save(User.create(sub, sub + "@test.com", "Owner"));
        Notebook notebook = notebookRepository.save(Notebook.create(owner.id(), "Notebook", null));

        mockMvc.perform(delete("/api/v1/notebooks/" + notebook.id())
                        .header("Authorization", "Bearer " + sub))
                .andExpect(status().isNoContent());

        assertThat(notebookJpaRepository.findById(notebook.id())).isEmpty();
    }

    @Test
    void firstAccessCreatesUserAndSubsequentAccessesReuseIt() throws Exception {
        String sub = "upsert-user-" + UUID.randomUUID();

        mockMvc.perform(get("/api/v1/notebooks").header("Authorization", "Bearer " + sub))
                .andExpect(status().isOk());
        assertThat(userRepository.findByCognitoSub(sub)).isPresent();

        mockMvc.perform(get("/api/v1/notebooks").header("Authorization", "Bearer " + sub))
                .andExpect(status().isOk());

        long count = userJpaRepository.findAll().stream().filter(u -> u.getCognitoSub().equals(sub)).count();
        assertThat(count).isEqualTo(1);
    }

    @Test
    void userCannotReadNotebookOfAnotherUser() throws Exception {
        String ownerSub = "owner-" + UUID.randomUUID();
        String intruderSub = "intruder-" + UUID.randomUUID();

        User owner = userRepository.save(User.create(ownerSub, ownerSub + "@test.com", "Owner"));
        Notebook notebook = notebookRepository.save(Notebook.create(owner.id(), "Private Notebook", null));

        mockMvc.perform(get("/api/v1/notebooks/" + notebook.id())
                        .header("Authorization", "Bearer " + intruderSub))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("NOTEBOOK_NOT_FOUND"));
    }

    @Test
    void userCannotUpdateNotebookOfAnotherUser() throws Exception {
        String ownerSub = "owner-" + UUID.randomUUID();
        String intruderSub = "intruder-" + UUID.randomUUID();

        User owner = userRepository.save(User.create(ownerSub, ownerSub + "@test.com", "Owner"));
        Notebook notebook = notebookRepository.save(Notebook.create(owner.id(), "Private Notebook", null));

        mockMvc.perform(patch("/api/v1/notebooks/" + notebook.id())
                        .header("Authorization", "Bearer " + intruderSub)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Hacked\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("NOTEBOOK_NOT_FOUND"));
    }

    @Test
    void userCannotDeleteNotebookOfAnotherUser() throws Exception {
        String ownerSub = "owner-" + UUID.randomUUID();
        String intruderSub = "intruder-" + UUID.randomUUID();

        User owner = userRepository.save(User.create(ownerSub, ownerSub + "@test.com", "Owner"));
        Notebook notebook = notebookRepository.save(Notebook.create(owner.id(), "Private Notebook", null));

        mockMvc.perform(delete("/api/v1/notebooks/" + notebook.id())
                        .header("Authorization", "Bearer " + intruderSub))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("NOTEBOOK_NOT_FOUND"));

        assertThat(notebookJpaRepository.findById(notebook.id())).isPresent();
    }

    @Test
    void listRespectsPaginationDefaultsAndParams() throws Exception {
        String sub = "paginated-" + UUID.randomUUID();
        User owner = userRepository.save(User.create(sub, sub + "@test.com", "Owner"));
        for (int i = 0; i < 3; i++) {
            notebookRepository.save(Notebook.create(owner.id(), "Notebook " + i, null));
        }

        mockMvc.perform(get("/api/v1/notebooks?page=0&size=2").header("Authorization", "Bearer " + sub))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.size").value(2));
    }

    @Test
    void listHonorsSortParam() throws Exception {
        String sub = "sorted-" + UUID.randomUUID();
        User owner = userRepository.save(User.create(sub, sub + "@test.com", "Owner"));
        notebookRepository.save(Notebook.create(owner.id(), "B", null));
        notebookRepository.save(Notebook.create(owner.id(), "A", null));

        mockMvc.perform(get("/api/v1/notebooks?sort=name,asc").header("Authorization", "Bearer " + sub))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].name").value("A"));
    }

    @Test
    void createReturns201AndPersists() throws Exception {
        String sub = "creator-" + UUID.randomUUID();

        mockMvc.perform(post("/api/v1/notebooks")
                        .header("Authorization", "Bearer " + sub)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"New Notebook\",\"description\":\"desc\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("New Notebook"));
    }

    @Test
    void createWithBlankNameReturns400ValidationError() throws Exception {
        String sub = "invalid-" + UUID.randomUUID();

        mockMvc.perform(post("/api/v1/notebooks")
                        .header("Authorization", "Bearer " + sub)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
    }
}
