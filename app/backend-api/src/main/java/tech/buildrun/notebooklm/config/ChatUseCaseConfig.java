package tech.buildrun.notebooklm.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tech.buildrun.notebooklm.core.port.in.SendMessagePort;
import tech.buildrun.notebooklm.core.port.out.ai.ChatCompletionPort;
import tech.buildrun.notebooklm.core.port.out.persistence.ConversationMessageRepositoryPort;
import tech.buildrun.notebooklm.core.port.out.persistence.ConversationRepositoryPort;
import tech.buildrun.notebooklm.core.port.out.persistence.SourceRepositoryPort;
import tech.buildrun.notebooklm.core.usecase.conversation.SendMessageUseCase;

@Configuration
public class ChatUseCaseConfig {

    @Bean
    public SendMessagePort sendMessagePort(ConversationRepositoryPort conversations,
                                           ConversationMessageRepositoryPort messages,
                                           SourceRepositoryPort sources,
                                           ChatCompletionPort chat) {
        return new SendMessageUseCase(conversations, messages, sources, chat);
    }
}
