package tech.buildrun.notebooklm.adapter.out.persistence;

import org.springframework.stereotype.Component;
import tech.buildrun.notebooklm.adapter.out.persistence.mapper.ConversationMapper;
import tech.buildrun.notebooklm.adapter.out.persistence.repository.ConversationJpaRepository;
import tech.buildrun.notebooklm.core.domain.conversation.Conversation;
import tech.buildrun.notebooklm.core.port.out.persistence.ConversationRepositoryPort;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class ConversationRepositoryAdapter implements ConversationRepositoryPort {

    private final ConversationJpaRepository repository;

    public ConversationRepositoryAdapter(ConversationJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<Conversation> findByIdAndOwnerId(UUID id, UUID ownerId) {
        return repository.findByIdAndOwnerId(id, ownerId).map(ConversationMapper::toDomain);
    }

    @Override
    public List<Conversation> findByNotebookIdAndOwnerIdOrderByCreatedAtDesc(UUID notebookId, UUID ownerId) {
        return repository.findByNotebookIdAndOwnerId(notebookId, ownerId).stream()
                .map(ConversationMapper::toDomain)
                .toList();
    }

    @Override
    public Conversation save(Conversation conversation) {
        return ConversationMapper.toDomain(repository.save(ConversationMapper.toEntity(conversation)));
    }

    @Override
    public void delete(Conversation conversation) {
        repository.deleteById(conversation.id());
    }
}
