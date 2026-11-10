package tech.buildrun.notebooklm.core.usecase;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tech.buildrun.notebooklm.core.domain.conversation.Conversation;
import tech.buildrun.notebooklm.core.domain.conversation.ConversationMessage;
import tech.buildrun.notebooklm.core.domain.conversation.MessageRole;
import tech.buildrun.notebooklm.core.domain.exception.ConversationNotFoundException;
import tech.buildrun.notebooklm.core.domain.exception.InvalidSourceIdsException;
import tech.buildrun.notebooklm.core.domain.exception.NotebookNotFoundException;
import tech.buildrun.notebooklm.core.domain.notebook.Notebook;
import tech.buildrun.notebooklm.core.domain.source.Source;
import tech.buildrun.notebooklm.core.domain.source.SourceStatus;
import tech.buildrun.notebooklm.core.domain.source.SourceType;
import tech.buildrun.notebooklm.core.port.in.ListConversationsPort.ConversationSummary;
import tech.buildrun.notebooklm.core.port.out.persistence.ConversationMessageRepositoryPort;
import tech.buildrun.notebooklm.core.port.out.persistence.ConversationRepositoryPort;
import tech.buildrun.notebooklm.core.port.out.persistence.NotebookRepositoryPort;
import tech.buildrun.notebooklm.core.port.out.persistence.SourceRepositoryPort;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConversationUseCasesTest {

    private final UUID ownerId = UUID.randomUUID();
    private final UUID notebookId = UUID.randomUUID();

    @Mock
    private NotebookRepositoryPort notebooks;

    @Mock
    private SourceRepositoryPort sources;

    @Mock
    private ConversationRepositoryPort conversations;

    @Mock
    private ConversationMessageRepositoryPort messages;

    private Notebook ownedNotebook() {
        return Notebook.restore(notebookId, ownerId, "n", null, Instant.now(), Instant.now());
    }

    private Source source(UUID id, UUID inNotebook, SourceStatus status) {
        return Source.restore(id, inNotebook, "doc.pdf", SourceType.FILE, "key", null, status, null, null);
    }

    private CreateConversationUseCase createUseCase() {
        return new CreateConversationUseCase(notebooks, sources, conversations);
    }

    @Test
    void createRejectsSourceIdThatDoesNotBelongToNotebook() {
        when(notebooks.findByIdAndOwnerId(notebookId, ownerId)).thenReturn(Optional.of(ownedNotebook()));
        UUID foreignSourceId = UUID.randomUUID();
        when(sources.findAllById(any(Collection.class)))
                .thenReturn(List.of(source(foreignSourceId, UUID.randomUUID(), SourceStatus.READY)));

        assertThatThrownBy(() -> createUseCase().execute(notebookId, ownerId, List.of(foreignSourceId)))
                .isInstanceOf(InvalidSourceIdsException.class);
        verify(conversations, never()).save(any());
    }

    @Test
    void createActivatesRequestedSources() {
        when(notebooks.findByIdAndOwnerId(notebookId, ownerId)).thenReturn(Optional.of(ownedNotebook()));
        UUID sourceId = UUID.randomUUID();
        when(sources.findAllById(any(Collection.class)))
                .thenReturn(List.of(source(sourceId, notebookId, SourceStatus.PENDING)));
        when(conversations.save(any(Conversation.class))).thenAnswer(i -> i.getArgument(0));

        Conversation created = createUseCase().execute(notebookId, ownerId, List.of(sourceId, sourceId));

        assertThat(created.activeSourceIds()).containsExactly(sourceId);
        assertThat(created.belongsTo(notebookId)).isTrue();
    }

    @Test
    void createWithoutRequestedSourcesActivatesReadySources() {
        when(notebooks.findByIdAndOwnerId(notebookId, ownerId)).thenReturn(Optional.of(ownedNotebook()));
        UUID readyId = UUID.randomUUID();
        when(sources.findByNotebookIdAndStatus(notebookId, SourceStatus.READY))
                .thenReturn(List.of(source(readyId, notebookId, SourceStatus.READY)));
        when(conversations.save(any(Conversation.class))).thenAnswer(i -> i.getArgument(0));

        assertThat(createUseCase().execute(notebookId, ownerId, null).activeSourceIds()).containsExactly(readyId);
        assertThat(createUseCase().execute(notebookId, ownerId, List.of()).activeSourceIds())
                .containsExactly(readyId);
    }

    @Test
    void createRejectsNotebookNotOwned() {
        when(notebooks.findByIdAndOwnerId(notebookId, ownerId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> createUseCase().execute(notebookId, ownerId, null))
                .isInstanceOf(NotebookNotFoundException.class);
    }

    @Test
    void deleteRejectsConversationFromAnotherNotebook() {
        UUID conversationId = UUID.randomUUID();
        Conversation conversation = Conversation.restore(conversationId, UUID.randomUUID(), Set.of(), Instant.now());
        when(conversations.findByIdAndOwnerId(conversationId, ownerId)).thenReturn(Optional.of(conversation));

        assertThatThrownBy(() -> new DeleteConversationUseCase(conversations)
                .execute(notebookId, conversationId, ownerId))
                .isInstanceOf(ConversationNotFoundException.class);
        verify(conversations, never()).delete(any());
    }

    @Test
    void deleteRemovesConversationOfNotebook() {
        UUID conversationId = UUID.randomUUID();
        Conversation conversation = Conversation.restore(conversationId, notebookId, Set.of(), Instant.now());
        when(conversations.findByIdAndOwnerId(conversationId, ownerId)).thenReturn(Optional.of(conversation));

        new DeleteConversationUseCase(conversations).execute(notebookId, conversationId, ownerId);

        verify(conversations).delete(conversation);
    }

    @Test
    void listBuildsPreviewFromFirstMessage() {
        UUID conversationId = UUID.randomUUID();
        Conversation conversation = Conversation.restore(conversationId, notebookId, Set.of(), Instant.now());
        Conversation empty = Conversation.restore(UUID.randomUUID(), notebookId, Set.of(), Instant.now());
        when(notebooks.findByIdAndOwnerId(notebookId, ownerId)).thenReturn(Optional.of(ownedNotebook()));
        when(conversations.findByNotebookIdAndOwnerIdOrderByCreatedAtDesc(notebookId, ownerId))
                .thenReturn(List.of(conversation, empty));
        when(messages.findFirstByConversationIdOrderByCreatedAtAsc(conversationId))
                .thenReturn(Optional.of(conversation.append(MessageRole.user, "x".repeat(60))));
        when(messages.findFirstByConversationIdOrderByCreatedAtAsc(empty.id())).thenReturn(Optional.empty());

        List<ConversationSummary> result = new ListConversationsUseCase(notebooks, conversations, messages)
                .execute(notebookId, ownerId);

        assertThat(result).extracting(ConversationSummary::preview).containsExactly("x".repeat(50) + "...", null);
    }

    @Test
    void listMessagesRequiresOwnedConversation() {
        UUID conversationId = UUID.randomUUID();
        when(conversations.findByIdAndOwnerId(conversationId, ownerId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> new ListMessagesUseCase(conversations, messages).execute(conversationId, ownerId))
                .isInstanceOf(ConversationNotFoundException.class);
    }

    @Test
    void listMessagesReturnsRepositoryMessages() {
        UUID conversationId = UUID.randomUUID();
        Conversation conversation = Conversation.restore(conversationId, notebookId, Set.of(), Instant.now());
        List<ConversationMessage> found = List.of(conversation.append(MessageRole.user, "hi"));
        when(conversations.findByIdAndOwnerId(conversationId, ownerId)).thenReturn(Optional.of(conversation));
        when(messages.findAllByConversationIdAndOwnerId(conversationId, ownerId)).thenReturn(found);

        assertThat(new ListMessagesUseCase(conversations, messages).execute(conversationId, ownerId))
                .isEqualTo(found);
    }
}
