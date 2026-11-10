package tech.buildrun.notebooklm.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tech.buildrun.notebooklm.core.port.in.CreateConversationPort;
import tech.buildrun.notebooklm.core.port.in.CreateNotebookPort;
import tech.buildrun.notebooklm.core.port.in.DeleteConversationPort;
import tech.buildrun.notebooklm.core.port.in.DeleteNotebookPort;
import tech.buildrun.notebooklm.core.port.in.GetNotebookPort;
import tech.buildrun.notebooklm.core.port.in.ListConversationsPort;
import tech.buildrun.notebooklm.core.port.in.ListMessagesPort;
import tech.buildrun.notebooklm.core.port.in.ListNotebooksPort;
import tech.buildrun.notebooklm.core.port.in.PatchNotebookPort;
import tech.buildrun.notebooklm.core.port.in.UpsertUserPort;
import tech.buildrun.notebooklm.core.port.out.persistence.ConversationMessageRepositoryPort;
import tech.buildrun.notebooklm.core.port.out.persistence.ConversationRepositoryPort;
import tech.buildrun.notebooklm.core.port.out.persistence.NotebookRepositoryPort;
import tech.buildrun.notebooklm.core.port.out.persistence.SourceRepositoryPort;
import tech.buildrun.notebooklm.core.port.out.persistence.UserRepositoryPort;
import tech.buildrun.notebooklm.core.usecase.CreateConversationUseCase;
import tech.buildrun.notebooklm.core.usecase.CreateNotebookUseCase;
import tech.buildrun.notebooklm.core.usecase.DeleteConversationUseCase;
import tech.buildrun.notebooklm.core.usecase.DeleteNotebookUseCase;
import tech.buildrun.notebooklm.core.usecase.GetNotebookUseCase;
import tech.buildrun.notebooklm.core.usecase.ListConversationsUseCase;
import tech.buildrun.notebooklm.core.usecase.ListMessagesUseCase;
import tech.buildrun.notebooklm.core.usecase.ListNotebooksUseCase;
import tech.buildrun.notebooklm.core.usecase.PatchNotebookUseCase;
import tech.buildrun.notebooklm.core.usecase.UpsertUserUseCase;

@Configuration
public class CrudUseCaseConfig {

    @Bean
    CreateNotebookPort createNotebookPort(NotebookRepositoryPort notebooks) {
        return new CreateNotebookUseCase(notebooks);
    }

    @Bean
    PatchNotebookPort patchNotebookPort(NotebookRepositoryPort notebooks) {
        return new PatchNotebookUseCase(notebooks);
    }

    @Bean
    ListNotebooksPort listNotebooksPort(NotebookRepositoryPort notebooks) {
        return new ListNotebooksUseCase(notebooks);
    }

    @Bean
    GetNotebookPort getNotebookPort(NotebookRepositoryPort notebooks, SourceRepositoryPort sources) {
        return new GetNotebookUseCase(notebooks, sources);
    }

    @Bean
    DeleteNotebookPort deleteNotebookPort(NotebookRepositoryPort notebooks) {
        return new DeleteNotebookUseCase(notebooks);
    }

    @Bean
    CreateConversationPort createConversationPort(NotebookRepositoryPort notebooks, SourceRepositoryPort sources,
                                                  ConversationRepositoryPort conversations) {
        return new CreateConversationUseCase(notebooks, sources, conversations);
    }

    @Bean
    ListConversationsPort listConversationsPort(NotebookRepositoryPort notebooks,
                                                ConversationRepositoryPort conversations,
                                                ConversationMessageRepositoryPort messages) {
        return new ListConversationsUseCase(notebooks, conversations, messages);
    }

    @Bean
    DeleteConversationPort deleteConversationPort(ConversationRepositoryPort conversations) {
        return new DeleteConversationUseCase(conversations);
    }

    @Bean
    ListMessagesPort listMessagesPort(ConversationRepositoryPort conversations,
                                      ConversationMessageRepositoryPort messages) {
        return new ListMessagesUseCase(conversations, messages);
    }

    @Bean
    UpsertUserPort upsertUserPort(UserRepositoryPort users) {
        return new UpsertUserUseCase(users);
    }
}
