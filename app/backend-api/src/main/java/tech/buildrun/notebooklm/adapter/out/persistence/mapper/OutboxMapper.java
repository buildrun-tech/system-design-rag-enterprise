package tech.buildrun.notebooklm.adapter.out.persistence.mapper;

import tech.buildrun.notebooklm.adapter.out.persistence.entity.OutboxJpaEntity;
import tech.buildrun.notebooklm.core.domain.outbox.OutboxEvent;

public final class OutboxMapper {

    private OutboxMapper() {
    }

    public static OutboxEvent toDomain(OutboxJpaEntity entity) {
        return OutboxEvent.restore(entity.getId(), entity.getType(), entity.getAggregateId(), entity.getPayload(),
                entity.getStatus(), entity.getAttempts(), entity.getNextAttemptAt(), entity.getLastError(),
                entity.getSentAt());
    }

    public static OutboxJpaEntity toEntity(OutboxEvent event) {
        OutboxJpaEntity entity = new OutboxJpaEntity(event.type(), event.aggregateId(), event.payload());
        copyState(event, entity);
        return entity;
    }

    public static void copyState(OutboxEvent event, OutboxJpaEntity entity) {
        entity.apply(event.status(), event.attempts(), event.nextAttemptAt(), event.lastError(), event.sentAt());
    }
}
