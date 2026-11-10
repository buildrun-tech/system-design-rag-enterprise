package tech.buildrun.notebooklm.adapter.out.persistence.mapper;

import tech.buildrun.notebooklm.adapter.out.persistence.entity.NotebookJpaEntity;
import tech.buildrun.notebooklm.core.domain.notebook.Notebook;

public final class NotebookMapper {

    private NotebookMapper() {
    }

    public static Notebook toDomain(NotebookJpaEntity entity) {
        return Notebook.restore(entity.getId(), entity.getOwnerId(), entity.getName(), entity.getDescription(),
                entity.getCreatedAt(), entity.getUpdatedAt());
    }

    public static NotebookJpaEntity toEntity(Notebook notebook) {
        return new NotebookJpaEntity(notebook.id(), notebook.ownerId(), notebook.name(), notebook.description(),
                notebook.createdAt(), notebook.updatedAt());
    }
}
