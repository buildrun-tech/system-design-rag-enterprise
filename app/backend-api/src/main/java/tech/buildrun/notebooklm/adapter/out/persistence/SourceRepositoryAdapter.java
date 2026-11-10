package tech.buildrun.notebooklm.adapter.out.persistence;

import org.springframework.stereotype.Repository;
import tech.buildrun.notebooklm.adapter.out.persistence.mapper.SourceMapper;
import tech.buildrun.notebooklm.adapter.out.persistence.repository.SourceJpaRepository;
import tech.buildrun.notebooklm.core.domain.source.Source;
import tech.buildrun.notebooklm.core.domain.source.SourceStatus;
import tech.buildrun.notebooklm.core.port.out.persistence.SourceRepositoryPort;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class SourceRepositoryAdapter implements SourceRepositoryPort {

    private final SourceJpaRepository repository;

    public SourceRepositoryAdapter(SourceJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<Source> findById(UUID id) {
        return repository.findById(id).map(SourceMapper::toDomain);
    }

    @Override
    public Optional<Source> findByIdAndNotebookId(UUID id, UUID notebookId) {
        return repository.findByIdAndNotebookId(id, notebookId).map(SourceMapper::toDomain);
    }

    @Override
    public List<Source> findAllById(Collection<UUID> ids) {
        return repository.findAllById(ids).stream().map(SourceMapper::toDomain).toList();
    }

    @Override
    public List<Source> findByNotebookId(UUID notebookId) {
        return repository.findByNotebookId(notebookId).stream().map(SourceMapper::toDomain).toList();
    }

    @Override
    public List<Source> findByNotebookIdAndStatus(UUID notebookId, SourceStatus status) {
        return repository.findByNotebookIdAndStatus(notebookId, status).stream().map(SourceMapper::toDomain).toList();
    }

    @Override
    public Source save(Source source) {
        return SourceMapper.toDomain(repository.saveAndFlush(SourceMapper.toEntity(source)));
    }

    @Override
    public void delete(Source source) {
        repository.deleteById(source.id());
    }
}
