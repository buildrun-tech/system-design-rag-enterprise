package tech.buildrun.notebooklm.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import tech.buildrun.notebooklm.AbstractIntegrationTest;
import tech.buildrun.notebooklm.entity.Conversation;
import tech.buildrun.notebooklm.entity.ConversationMessage;
import tech.buildrun.notebooklm.entity.MessageRole;
import tech.buildrun.notebooklm.entity.Notebook;
import tech.buildrun.notebooklm.entity.User;
import tech.buildrun.notebooklm.repository.ConversationMessageRepository;
import tech.buildrun.notebooklm.repository.ConversationRepository;
import tech.buildrun.notebooklm.repository.NotebookRepository;
import tech.buildrun.notebooklm.repository.UserRepository;
import tech.buildrun.notebooklm.security.TestJwtDecoderConfig;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestJwtDecoderConfig.class)
class ConversationMessageApiIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private NotebookRepository notebookRepository;

    @Autowired
    private ConversationRepository conversationRepository;

    @Autowired
    private ConversationMessageRepository conversationMessageRepository;

    @Test
    void userCannotReadMessagesOfConversationOwnedByAnotherUserTwoHops() throws Exception {
        String ownerSub = "owner-" + UUID.randomUUID();
        String intruderSub = "intruder-" + UUID.randomUUID();

        User owner = userRepository.save(new User(ownerSub, ownerSub + "@test.com", "Owner"));
        Notebook notebook = notebookRepository.save(new Notebook(owner, "Notebook", null));
        Conversation conversation = conversationRepository.save(new Conversation(notebook));
        conversationMessageRepository.save(new ConversationMessage(conversation, MessageRole.user, "secret question"));

        mockMvc.perform(get("/api/v1/conversations/" + conversation.getId() + "/messages")
                        .header("Authorization", "Bearer " + intruderSub))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("CONVERSATION_NOT_FOUND"));
    }

    @Test
    void ownerReadsMessagesInChronologicalOrder() throws Exception {
        String ownerSub = "owner-" + UUID.randomUUID();

        User owner = userRepository.save(new User(ownerSub, ownerSub + "@test.com", "Owner"));
        Notebook notebook = notebookRepository.save(new Notebook(owner, "Notebook", null));
        Conversation conversation = conversationRepository.save(new Conversation(notebook));
        conversationMessageRepository.save(new ConversationMessage(conversation, MessageRole.user, "question"));
        conversationMessageRepository.save(new ConversationMessage(conversation, MessageRole.assistant, "answer"));

        mockMvc.perform(get("/api/v1/conversations/" + conversation.getId() + "/messages")
                        .header("Authorization", "Bearer " + ownerSub))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].content").value("question"))
                .andExpect(jsonPath("$[1].content").value("answer"));
    }
}
