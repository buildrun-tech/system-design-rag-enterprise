package tech.buildrun.notebooklm.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import tech.buildrun.notebooklm.dto.ConversationCreateRequest;
import tech.buildrun.notebooklm.entity.Notebook;
import tech.buildrun.notebooklm.entity.Source;
import tech.buildrun.notebooklm.entity.SourceType;
import tech.buildrun.notebooklm.entity.User;
import tech.buildrun.notebooklm.exception.InvalidSourceIdsException;
import tech.buildrun.notebooklm.repository.ConversationMessageRepository;
import tech.buildrun.notebooklm.repository.ConversationRepository;
import tech.buildrun.notebooklm.repository.SourceRepository;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConversationServiceTest {

    @Mock
    private ConversationRepository conversationRepository;

    @Mock
    private ConversationMessageRepository conversationMessageRepository;

    @Mock
    private SourceRepository sourceRepository;

    @Mock
    private NotebookService notebookService;

    @InjectMocks
    private ConversationService conversationService;

    @Test
    void createRejectsSourceIdThatDoesNotBelongToNotebook() {
        User owner = new User("sub", "owner@test.com", "Owner");
        Notebook notebook = new Notebook(owner, "Notebook", null);
        UUID notebookId = UUID.randomUUID();
        ReflectionTestUtils.setField(notebook, "id", notebookId);

        Source ownSource = new Source(notebook, "own.pdf", SourceType.FILE, "key", null);
        ReflectionTestUtils.setField(ownSource, "id", UUID.randomUUID());

        UUID foreignSourceId = UUID.randomUUID();

        when(notebookService.getOwnedOrThrow(any(), any())).thenReturn(notebook);
        when(sourceRepository.findByNotebook_Id(notebookId)).thenReturn(List.of(ownSource));

        assertThatThrownBy(() -> conversationService.create(
                notebookId, UUID.randomUUID(), new ConversationCreateRequest(List.of(foreignSourceId))))
                .isInstanceOf(InvalidSourceIdsException.class);
    }
}
