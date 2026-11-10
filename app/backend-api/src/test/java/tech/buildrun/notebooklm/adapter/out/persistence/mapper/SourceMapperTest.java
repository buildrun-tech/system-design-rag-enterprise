package tech.buildrun.notebooklm.adapter.out.persistence.mapper;

import org.junit.jupiter.api.Test;
import tech.buildrun.notebooklm.adapter.out.persistence.entity.SourceJpaEntity;
import tech.buildrun.notebooklm.core.domain.source.Source;
import tech.buildrun.notebooklm.core.domain.source.SourceStatus;
import tech.buildrun.notebooklm.core.domain.source.SourceType;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SourceMapperTest {

    @Test
    void roundTripPreservesAllFields() {
        Source source = Source.restore(UUID.randomUUID(), UUID.randomUUID(), "doc.pdf", SourceType.FILE, "k", null,
                SourceStatus.FAILED, "boom", Instant.now());

        SourceJpaEntity entity = SourceMapper.toEntity(source);
        Source back = SourceMapper.toDomain(entity);

        assertThat(entity.getNotebookId()).isEqualTo(source.notebookId());
        assertThat(back).usingRecursiveComparison().isEqualTo(source);
    }
}
