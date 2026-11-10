package tech.buildrun.notebooklm.adapter.in.web;

import io.awspring.cloud.s3.S3Resource;
import io.awspring.cloud.s3.S3Template;
import io.awspring.cloud.sqs.operations.SqsTemplate;
import org.junit.jupiter.api.Test;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tech.buildrun.notebooklm.AbstractIntegrationTest;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestJwtDecoderConfig.class)
class SourceApiIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepositoryPort userRepository;

    @Autowired
    private NotebookRepositoryPort notebookRepository;

    @Autowired
    private SourceRepositoryPort sourceRepository;

    @MockitoBean
    private S3Template s3Template;

    @MockitoBean
    private SqsTemplate sqsTemplate;

    @MockitoBean
    private VectorStore vectorStore;

    @Test
    void uploadPdfPersistsPendingSourceUploadsToS3AndPublishesSqs() throws Exception {
        String sub = "owner-" + UUID.randomUUID();
        User owner = userRepository.save(User.create(sub, sub + "@test.com", "Owner"));
        Notebook notebook = notebookRepository.save(Notebook.create(owner.id(), "Notebook", null));

        when(s3Template.upload(any(), any(), any(), any())).thenReturn(mock(S3Resource.class));

        MockMultipartFile file = new MockMultipartFile("file", "doc.pdf", "application/pdf", "content".getBytes());

        mockMvc.perform(multipart("/api/v1/notebooks/" + notebook.id() + "/sources")
                        .file(file)
                        .header("Authorization", "Bearer " + sub))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.name").value("doc.pdf"))
                .andExpect(jsonPath("$.status").value("PENDING"));

        verify(s3Template).upload(any(), any(), any(), any());
        verify(sqsTemplate).send(any(), any(tech.buildrun.notebooklm.adapter.out.queue.IngestionMessage.class));
    }

    @Test
    void uploadUnsupportedExtensionReturns415() throws Exception {
        String sub = "owner-" + UUID.randomUUID();
        User owner = userRepository.save(User.create(sub, sub + "@test.com", "Owner"));
        Notebook notebook = notebookRepository.save(Notebook.create(owner.id(), "Notebook", null));

        MockMultipartFile file = new MockMultipartFile("file", "sheet.xlsx", "application/vnd.ms-excel", "content".getBytes());

        mockMvc.perform(multipart("/api/v1/notebooks/" + notebook.id() + "/sources")
                        .file(file)
                        .header("Authorization", "Bearer " + sub))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.error").value("UNSUPPORTED_FILE_TYPE"));
    }

    @Test
    void uploadToNotebookOfAnotherUserReturns404() throws Exception {
        String ownerSub = "owner-" + UUID.randomUUID();
        String intruderSub = "intruder-" + UUID.randomUUID();
        User owner = userRepository.save(User.create(ownerSub, ownerSub + "@test.com", "Owner"));
        Notebook notebook = notebookRepository.save(Notebook.create(owner.id(), "Notebook", null));

        MockMultipartFile file = new MockMultipartFile("file", "doc.pdf", "application/pdf", "content".getBytes());

        mockMvc.perform(multipart("/api/v1/notebooks/" + notebook.id() + "/sources")
                        .file(file)
                        .header("Authorization", "Bearer " + intruderSub))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("NOTEBOOK_NOT_FOUND"));
    }

    @Test
    void listReturnsSourcesOfNotebook() throws Exception {
        String sub = "owner-" + UUID.randomUUID();
        User owner = userRepository.save(User.create(sub, sub + "@test.com", "Owner"));
        Notebook notebook = notebookRepository.save(Notebook.create(owner.id(), "Notebook", null));
        sourceRepository.save(Source.create(notebook.id(), "doc.pdf", SourceType.FILE, "key", null));

        mockMvc.perform(get("/api/v1/notebooks/" + notebook.id() + "/sources")
                        .header("Authorization", "Bearer " + sub))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("doc.pdf"));
    }

    @Test
    void getReturnsSourceStatus() throws Exception {
        String sub = "owner-" + UUID.randomUUID();
        User owner = userRepository.save(User.create(sub, sub + "@test.com", "Owner"));
        Notebook notebook = notebookRepository.save(Notebook.create(owner.id(), "Notebook", null));
        Source source = sourceRepository.save(Source.create(notebook.id(), "doc.pdf", SourceType.FILE, "key", null));

        mockMvc.perform(get("/api/v1/notebooks/" + notebook.id() + "/sources/" + source.id())
                        .header("Authorization", "Bearer " + sub))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void getUnknownSourceReturns404() throws Exception {
        String sub = "owner-" + UUID.randomUUID();
        User owner = userRepository.save(User.create(sub, sub + "@test.com", "Owner"));
        Notebook notebook = notebookRepository.save(Notebook.create(owner.id(), "Notebook", null));

        mockMvc.perform(get("/api/v1/notebooks/" + notebook.id() + "/sources/" + UUID.randomUUID())
                        .header("Authorization", "Bearer " + sub))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("SOURCE_NOT_FOUND"));
    }

    @Test
    void deleteRemovesSourceS3ObjectAndVectorStoreEntries() throws Exception {
        String sub = "owner-" + UUID.randomUUID();
        User owner = userRepository.save(User.create(sub, sub + "@test.com", "Owner"));
        Notebook notebook = notebookRepository.save(Notebook.create(owner.id(), "Notebook", null));
        Source source = sourceRepository.save(Source.create(notebook.id(), "doc.pdf", SourceType.FILE, "some/key", null));

        mockMvc.perform(delete("/api/v1/notebooks/" + notebook.id() + "/sources/" + source.id())
                        .header("Authorization", "Bearer " + sub))
                .andExpect(status().isNoContent());

        assertThat(sourceRepository.findById(source.id())).isEmpty();
        verify(s3Template).deleteObject(any(), org.mockito.ArgumentMatchers.eq("some/key"));
        verify(vectorStore).delete("source_id == '" + source.id() + "'");
    }
}
