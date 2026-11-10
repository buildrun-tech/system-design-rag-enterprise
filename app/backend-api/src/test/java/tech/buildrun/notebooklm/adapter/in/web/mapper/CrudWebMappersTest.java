package tech.buildrun.notebooklm.adapter.in.web.mapper;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import tech.buildrun.notebooklm.adapter.in.web.dto.ConversationDetailResponse;
import tech.buildrun.notebooklm.adapter.in.web.dto.ConversationMessageResponse;
import tech.buildrun.notebooklm.adapter.in.web.dto.ConversationResponse;
import tech.buildrun.notebooklm.adapter.in.web.dto.NotebookDetailResponse;
import tech.buildrun.notebooklm.adapter.in.web.dto.NotebookResponse;
import tech.buildrun.notebooklm.core.domain.conversation.Conversation;
import tech.buildrun.notebooklm.core.domain.conversation.ConversationMessage;
import tech.buildrun.notebooklm.core.domain.conversation.MessageRole;
import tech.buildrun.notebooklm.core.domain.notebook.Notebook;
import tech.buildrun.notebooklm.core.domain.shared.PageResult;
import tech.buildrun.notebooklm.core.domain.source.Source;
import tech.buildrun.notebooklm.core.domain.source.SourceStatus;
import tech.buildrun.notebooklm.core.domain.source.SourceType;
import tech.buildrun.notebooklm.core.port.in.GetNotebookPort.NotebookDetail;
import tech.buildrun.notebooklm.core.port.in.ListConversationsPort.ConversationSummary;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CrudWebMappersTest {

    private final Instant now = Instant.now();
    private final Notebook notebook = Notebook.restore(UUID.randomUUID(), UUID.randomUUID(), "n", "d", now, now);

    @Test
    void notebookResponseCarriesAllFields() {
        NotebookResponse response = NotebookWebMapper.toResponse(notebook);

        assertThat(response).isEqualTo(new NotebookResponse(notebook.id(), "n", "d", now, now));
    }

    @Test
    void notebookPageKeepsTotalsAndPageable() {
        PageRequest pageable = PageRequest.of(0, 2);

        PageImpl<NotebookResponse> page = NotebookWebMapper.toPage(
                new PageResult<>(List.of(notebook, notebook), 0, 2, 3, 2), pageable);

        assertThat(page.getContent()).hasSize(2);
        assertThat(page.getTotalElements()).isEqualTo(3);
        assertThat(page.getSize()).isEqualTo(2);
    }

    @Test
    void notebookDetailIncludesSources() {
        Source source = Source.restore(UUID.randomUUID(), notebook.id(), "doc.pdf", SourceType.FILE, "k", null,
                SourceStatus.READY, null, now);

        NotebookDetailResponse response = NotebookWebMapper.toDetailResponse(
                new NotebookDetail(notebook, List.of(source)));

        assertThat(response.sources()).containsExactly(new NotebookDetailResponse.SourceItem(
                source.id(), "doc.pdf", SourceType.FILE, SourceStatus.READY, null, now));
    }

    @Test
    void conversationResponsesCarryFields() {
        UUID sourceId = UUID.randomUUID();
        Conversation conversation = Conversation.restore(UUID.randomUUID(), notebook.id(), Set.of(sourceId), now);

        ConversationDetailResponse detail = ConversationWebMapper.toDetailResponse(conversation);
        ConversationResponse summary = ConversationWebMapper.toResponse(new ConversationSummary(conversation, "p"));

        assertThat(detail).isEqualTo(new ConversationDetailResponse(conversation.id(), notebook.id(),
                List.of(sourceId), now));
        assertThat(summary).isEqualTo(new ConversationResponse(conversation.id(), notebook.id(), now, "p"));
    }

    @Test
    void messageResponseCarriesFields() {
        ConversationMessage message = ConversationMessage.restore(UUID.randomUUID(), UUID.randomUUID(),
                MessageRole.user, "hi", List.of(), now);

        ConversationMessageResponse response = ConversationWebMapper.toResponse(message);

        assertThat(response).isEqualTo(new ConversationMessageResponse(message.id(), MessageRole.user, "hi",
                List.of(), now));
    }
}
