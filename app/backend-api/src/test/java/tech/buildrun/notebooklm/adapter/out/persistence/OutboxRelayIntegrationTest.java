package tech.buildrun.notebooklm.adapter.out.persistence;

import io.awspring.cloud.sqs.operations.SqsTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tech.buildrun.notebooklm.AbstractIntegrationTest;
import tech.buildrun.notebooklm.adapter.out.persistence.repository.OutboxJpaRepository;
import tech.buildrun.notebooklm.core.domain.outbox.OutboxEvent;
import tech.buildrun.notebooklm.core.domain.outbox.OutboxEventType;
import tech.buildrun.notebooklm.core.domain.outbox.OutboxStatus;
import tech.buildrun.notebooklm.core.port.in.RelayOutboxPort;
import tech.buildrun.notebooklm.core.port.out.MessagePublisherPort;
import tech.buildrun.notebooklm.core.port.out.persistence.OutboxRepositoryPort;
import tech.buildrun.notebooklm.security.TestJwtDecoderConfig;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

@SpringBootTest
@Import(TestJwtDecoderConfig.class)
class OutboxRelayIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private OutboxRepositoryPort outbox;

    @Autowired
    private OutboxJpaRepository jpa;

    @Autowired
    private RelayOutboxPort relay;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @MockitoBean
    private MessagePublisherPort publisher;

    @MockitoBean
    private SqsTemplate sqsTemplate;

    @MockitoBean
    private VectorStore vectorStore;

    @BeforeEach
    void clean() {
        jpa.deleteAll();
    }

    private OutboxEvent save() {
        UUID id = UUID.randomUUID();
        return outbox.save(OutboxEvent.create(OutboxEventType.INGESTION_REQUESTED, id,
                Map.of("sourceId", id.toString(), "userId", UUID.randomUUID().toString()), Instant.now().minusSeconds(1)));
    }

    @Test
    void relayMarksSentOnSuccess() {
        OutboxEvent saved = save();

        assertThat(relay.relay()).isEqualTo(1);

        var row = jpa.findById(saved.id()).orElseThrow();
        assertThat(row.getStatus()).isEqualTo(OutboxStatus.SENT);
        assertThat(row.getSentAt()).isNotNull();
    }

    @Test
    void publishFailureKeepsPendingForRetry() {
        OutboxEvent saved = save();
        doThrow(new IllegalStateException("sqs down")).when(publisher).publish(any());

        relay.relay();

        var row = jpa.findById(saved.id()).orElseThrow();
        assertThat(row.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(row.getAttempts()).isEqualTo(1);
        assertThat(row.getNextAttemptAt()).isAfter(Instant.now());
        assertThat(relay.relay()).isZero();
    }

    @Test
    void concurrentClaimsNeverOverlap() throws Exception {
        for (int i = 0; i < 6; i++) save();
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        CountDownLatch bothClaimed = new CountDownLatch(2);
        List<List<OutboxEvent>> claims = new java.util.concurrent.CopyOnWriteArrayList<>();

        Runnable claimAndHold = () -> tx.executeWithoutResult(status -> {
            claims.add(outbox.claimBatch(Instant.now(), 3));
            bothClaimed.countDown();
            try {
                bothClaimed.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        Thread a = new Thread(claimAndHold);
        Thread b = new Thread(claimAndHold);
        a.start();
        b.start();
        a.join();
        b.join();

        Set<UUID> ids = new java.util.HashSet<>();
        claims.forEach(batch -> batch.forEach(event -> assertThat(ids.add(event.id())).isTrue()));
        assertThat(ids).hasSize(6);
    }

    @Test
    void rollbackDiscardsOutboxEvent() {
        UUID aggregateId = UUID.randomUUID();

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            outbox.save(OutboxEvent.create(OutboxEventType.INGESTION_REQUESTED, aggregateId,
                    Map.of("sourceId", aggregateId.toString(), "userId", UUID.randomUUID().toString()), Instant.now()));
            status.setRollbackOnly();
        });

        assertThat(jpa.count()).isZero();
    }

    @Test
    void purgeRemovesOnlyOldSent() {
        OutboxEvent sent = save();
        sent.markSent(Instant.now().minusSeconds(3600));
        outbox.save(sent);
        OutboxEvent pending = save();

        int removed = new TransactionTemplate(transactionManager)
                .execute(status -> outbox.deleteSentBefore(Instant.now().minusSeconds(60)));

        assertThat(removed).isEqualTo(1);
        assertThat(jpa.findById(pending.id())).isPresent();
    }
}
