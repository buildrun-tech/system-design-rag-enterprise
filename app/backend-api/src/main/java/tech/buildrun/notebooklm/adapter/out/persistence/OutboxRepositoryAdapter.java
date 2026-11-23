package tech.buildrun.notebooklm.adapter.out.persistence;

import org.springframework.stereotype.Component;
import tech.buildrun.notebooklm.adapter.out.persistence.entity.OutboxJpaEntity;
import tech.buildrun.notebooklm.adapter.out.persistence.mapper.OutboxMapper;
import tech.buildrun.notebooklm.adapter.out.persistence.repository.OutboxJpaRepository;
import tech.buildrun.notebooklm.core.domain.outbox.OutboxEvent;
import tech.buildrun.notebooklm.core.port.out.persistence.OutboxRepositoryPort;

import java.time.Instant;
import java.util.List;

@Component
public class OutboxRepositoryAdapter implements OutboxRepositoryPort {

    private final OutboxJpaRepository repository;

    public OutboxRepositoryAdapter(OutboxJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public OutboxEvent save(OutboxEvent event) {
        OutboxJpaEntity entity = event.id() == null
                ? OutboxMapper.toEntity(event)
                : repository.findById(event.id()).orElseGet(() -> OutboxMapper.toEntity(event));
        OutboxMapper.copyState(event, entity);
        return OutboxMapper.toDomain(repository.saveAndFlush(entity));
    }

    @Override
    public List<OutboxEvent> claimBatch(Instant now, int limit) {
        return repository.claimBatch(now, limit).stream().map(OutboxMapper::toDomain).toList();
    }

    @Override
    public int deleteSentBefore(Instant threshold) {
        return repository.deleteSentBefore(threshold);
    }
}
