package tech.buildrun.notebooklm.adapter.out.persistence.mapper;

import tech.buildrun.notebooklm.adapter.out.persistence.entity.SourceJpaEntity;
import tech.buildrun.notebooklm.core.domain.source.Source;

public final class SourceMapper {

    private SourceMapper() {
    }

    public static Source toDomain(SourceJpaEntity entity) {
        return Source.restore(entity.getId(), entity.getNotebookId(), entity.getName(), entity.getType(),
                entity.getS3Key(), entity.getUrl(), entity.getStatus(), entity.getErrorMessage(),
                entity.getCreatedAt());
    }

    public static SourceJpaEntity toEntity(Source source) {
        return new SourceJpaEntity(source.id(), source.notebookId(), source.name(), source.type(), source.s3Key(),
                source.url(), source.status(), source.errorMessage(), source.createdAt());
    }
}
