package tech.buildrun.notebooklm.adapter.in.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import tech.buildrun.notebooklm.AbstractIntegrationTest;
import tech.buildrun.notebooklm.core.domain.conversation.Conversation;
import tech.buildrun.notebooklm.core.domain.conversation.ConversationMessage;
import tech.buildrun.notebooklm.core.domain.conversation.MessageRole;
import tech.buildrun.notebooklm.core.domain.notebook.Notebook;
import tech.buildrun.notebooklm.core.domain.user.User;
import tech.buildrun.notebooklm.core.port.out.persistence.ConversationMessageRepositoryPort;
import tech.buildrun.notebooklm.core.port.out.persistence.ConversationRepositoryPort;
import tech.buildrun.notebooklm.core.port.out.persistence.NotebookRepositoryPort;
import tech.buildrun.notebooklm.core.port.out.persistence.UserRepositoryPort;
import tech.buildrun.notebooklm.security.TestJwtDecoderConfig;
import org.springframework.test.web.servlet.MockMvc;

import tech.buildrun.notebooklm.core.domain.conversation.CitedChunk;
import tech.buildrun.notebooklm.core.domain.conversation.CitedSource;

import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestJwtDecoderConfig.class)
class ConversationMessageApiIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepositoryPort userRepository;

    @Autowired
    private NotebookRepositoryPort notebookRepository;

    @Autowired
    private ConversationRepositoryPort conversationRepository;

    @Autowired
    private ConversationMessageRepositoryPort conversationMessageRepository;

    @Test
    void userCannotReadMessagesOfConversationOwnedByAnotherUserTwoHops() throws Exception {
        String ownerSub = "owner-" + UUID.randomUUID();
        String intruderSub = "intruder-" + UUID.randomUUID();

        User owner = userRepository.save(User.create(ownerSub, ownerSub + "@test.com", "Owner"));
        Notebook notebook = notebookRepository.save(Notebook.create(owner.id(), "Notebook", null));
        Conversation conversation = conversationRepository.save(Conversation.create(notebook.id()));
        conversationMessageRepository.save(conversation.append(MessageRole.user, "secret question"));

        mockMvc.perform(get("/api/v1/conversations/" + conversation.id() + "/messages")
                        .header("Authorization", "Bearer " + intruderSub))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("CONVERSATION_NOT_FOUND"));
    }

    @Test
    void ownerReadsMessagesInChronologicalOrder() throws Exception {
        String ownerSub = "owner-" + UUID.randomUUID();

        User owner = userRepository.save(User.create(ownerSub, ownerSub + "@test.com", "Owner"));
        Notebook notebook = notebookRepository.save(Notebook.create(owner.id(), "Notebook", null));
        Conversation conversation = conversationRepository.save(Conversation.create(notebook.id()));
        conversationMessageRepository.save(conversation.append(MessageRole.user, "question"));
        conversationMessageRepository.save(conversation.append(MessageRole.assistant, "answer"));

        mockMvc.perform(get("/api/v1/conversations/" + conversation.id() + "/messages")
                        .header("Authorization", "Bearer " + ownerSub))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].content").value("question"))
                .andExpect(jsonPath("$[1].content").value("answer"));
    }

    @Test
    void ownerReadsPersistedSourcesOfAssistantMessageAndEmptyListForUserMessage() throws Exception {
        String ownerSub = "owner-" + UUID.randomUUID();
        UUID sourceId = UUID.randomUUID();

        User owner = userRepository.save(User.create(ownerSub, ownerSub + "@test.com", "Owner"));
        Notebook notebook = notebookRepository.save(Notebook.create(owner.id(), "Notebook", null));
        Conversation conversation = conversationRepository.save(Conversation.create(notebook.id()));
        conversationMessageRepository.save(conversation.append(MessageRole.user, "question"));
        conversationMessageRepository.save(conversation.append(MessageRole.assistant, "answer",
                List.of(new CitedSource(sourceId, "contrato.pdf", List.of(new CitedChunk(2, "trecho do contrato"))))));

        mockMvc.perform(get("/api/v1/conversations/" + conversation.id() + "/messages")
                        .header("Authorization", "Bearer " + ownerSub))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].sources").isEmpty())
                .andExpect(jsonPath("$[1].sources[0].sourceId").value(sourceId.toString()))
                .andExpect(jsonPath("$[1].sources[0].name").value("contrato.pdf"))
                .andExpect(jsonPath("$[1].sources[0].chunks[0].chunkIndex").value(2))
                .andExpect(jsonPath("$[1].sources[0].chunks[0].snippet").value("trecho do contrato"));
    }

    @Test
    void userCannotSendMessageToConversationOwnedByAnotherUserTwoHops() throws Exception {
        String ownerSub = "owner-" + UUID.randomUUID();
        String intruderSub = "intruder-" + UUID.randomUUID();

        User owner = userRepository.save(User.create(ownerSub, ownerSub + "@test.com", "Owner"));
        Notebook notebook = notebookRepository.save(Notebook.create(owner.id(), "Notebook", null));
        Conversation conversation = conversationRepository.save(Conversation.create(notebook.id()));

        mockMvc.perform(post("/api/v1/conversations/" + conversation.id() + "/messages")
                        .header("Authorization", "Bearer " + intruderSub)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"pergunta\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("CONVERSATION_NOT_FOUND"));
    }

    @Test
    void sendMessageWithBlankContentFailsValidationWithoutCallingLlm() throws Exception {
        String ownerSub = "owner-" + UUID.randomUUID();

        User owner = userRepository.save(User.create(ownerSub, ownerSub + "@test.com", "Owner"));
        Notebook notebook = notebookRepository.save(Notebook.create(owner.id(), "Notebook", null));
        Conversation conversation = conversationRepository.save(Conversation.create(notebook.id()));

        mockMvc.perform(post("/api/v1/conversations/" + conversation.id() + "/messages")
                        .header("Authorization", "Bearer " + ownerSub)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
    }
}
