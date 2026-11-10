package tech.buildrun.notebooklm.adapter.out.persistence;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import tech.buildrun.notebooklm.adapter.out.persistence.entity.NotebookJpaEntity;
import tech.buildrun.notebooklm.adapter.out.persistence.mapper.NotebookMapper;
import tech.buildrun.notebooklm.adapter.out.persistence.repository.NotebookJpaRepository;
import tech.buildrun.notebooklm.core.domain.notebook.Notebook;
import tech.buildrun.notebooklm.core.domain.shared.PageQuery;
import tech.buildrun.notebooklm.core.domain.shared.PageResult;
import tech.buildrun.notebooklm.core.port.out.persistence.NotebookRepositoryPort;

import java.util.Optional;
import java.util.UUID;

@Component
public class NotebookRepositoryAdapter implements NotebookRepositoryPort {

    private final NotebookJpaRepository repository;

    public NotebookRepositoryAdapter(NotebookJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<Notebook> findByIdAndOwnerId(UUID id, UUID ownerId) {
        return repository.findByIdAndOwnerId(id, ownerId).map(NotebookMapper::toDomain);
    }

    @Override
    public PageResult<Notebook> findByOwnerId(UUID ownerId, PageQuery pageQuery) {
        Sort sort = pageQuery.orders().isEmpty()
                ? Sort.by(Sort.Direction.DESC, "createdAt")
                : Sort.by(pageQuery.orders().stream()
                .map(o -> new Sort.Order(o.ascending() ? Sort.Direction.ASC : Sort.Direction.DESC, o.property()))
                .toList());
        PageRequest request = PageRequest.of(pageQuery.page(), pageQuery.size(), sort);
        Page<NotebookJpaEntity> page = repository.findByOwnerId(ownerId, request);
        return new PageResult<>(page.getContent().stream().map(NotebookMapper::toDomain).toList(),
                page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }

    @Override
    public Notebook save(Notebook notebook) {
        return NotebookMapper.toDomain(repository.save(NotebookMapper.toEntity(notebook)));
    }

    @Override
    public void delete(Notebook notebook) {
        repository.deleteById(notebook.id());
    }
}
