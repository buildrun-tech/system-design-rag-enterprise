package tech.buildrun.notebooklm.adapter.out.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import tech.buildrun.notebooklm.adapter.out.persistence.entity.OutboxJpaEntity;
import tech.buildrun.notebooklm.core.domain.outbox.OutboxStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface OutboxJpaRepository extends JpaRepository<OutboxJpaEntity, UUID> {

    @Query(value = """
            SELECT * FROM tb_outbox
            WHERE status = 'PENDING' AND next_attempt_at <= :now
            ORDER BY next_attempt_at
            LIMIT :limit
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<OutboxJpaEntity> claimBatch(@Param("now") Instant now, @Param("limit") int limit);

    @Modifying
    @Query("DELETE FROM OutboxJpaEntity o WHERE o.status = 'SENT' AND o.sentAt < :threshold")
    int deleteSentBefore(@Param("threshold") Instant threshold);

    long countByStatus(OutboxStatus status);

    @Query("SELECT MIN(o.createdAt) FROM OutboxJpaEntity o WHERE o.status = 'PENDING'")
    Instant oldestPendingCreatedAt();
}
