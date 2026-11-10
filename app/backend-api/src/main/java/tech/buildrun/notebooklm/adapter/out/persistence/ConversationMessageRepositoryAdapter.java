package tech.buildrun.notebooklm.adapter.out.persistence;

import org.springframework.stereotype.Component;
import tech.buildrun.notebooklm.adapter.out.persistence.mapper.ConversationMessageMapper;
import tech.buildrun.notebooklm.adapter.out.persistence.repository.ConversationMessageJpaRepository;
import tech.buildrun.notebooklm.core.domain.conversation.ConversationMessage;
import tech.buildrun.notebooklm.core.port.out.persistence.ConversationMessageRepositoryPort;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class ConversationMessageRepositoryAdapter implements ConversationMessageRepositoryPort {

    private final ConversationMessageJpaRepository repository;

    public ConversationMessageRepositoryAdapter(ConversationMessageJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public List<ConversationMessage> findAllByConversationIdAndOwnerId(UUID conversationId, UUID ownerId) {
        return repository.findAllByConversationIdAndOwnerId(conversationId, ownerId).stream()
                .map(ConversationMessageMapper::toDomain)
                .toList();
    }

    @Override
    public Optional<ConversationMessage> findFirstByConversationIdOrderByCreatedAtAsc(UUID conversationId) {
        return repository.findFirstByConversationIdOrderByCreatedAtAsc(conversationId)
                .map(ConversationMessageMapper::toDomain);
    }

    @Override
    public List<ConversationMessage> findTop10ByConversationIdOrderByCreatedAtDesc(UUID conversationId) {
        return repository.findTop10ByConversationIdOrderByCreatedAtDesc(conversationId).stream()
                .map(ConversationMessageMapper::toDomain)
                .toList();
    }

    @Override
    public ConversationMessage save(ConversationMessage message) {
        return ConversationMessageMapper.toDomain(repository.save(ConversationMessageMapper.toEntity(message)));
    }
}
