package tech.buildrun.notebooklm.config;

import io.awspring.cloud.autoconfigure.sqs.SqsAsyncClientCustomizer;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tech.buildrun.notebooklm.core.port.in.PurgeOutboxPort;
import tech.buildrun.notebooklm.core.port.in.RelayOutboxPort;
import tech.buildrun.notebooklm.core.port.out.MessagePublisherPort;
import tech.buildrun.notebooklm.core.port.out.persistence.OutboxRepositoryPort;
import tech.buildrun.notebooklm.core.usecase.outbox.PurgeSentOutboxUseCase;
import tech.buildrun.notebooklm.core.usecase.outbox.RelayOutboxUseCase;

import java.time.Clock;
import java.time.Duration;

@Configuration
@EnableScheduling
@EnableConfigurationProperties(OutboxProperties.class)
public class OutboxConfig {

    @Bean
    SqsAsyncClientCustomizer sqsCallTimeouts() {
        return builder -> builder.overrideConfiguration(config -> config
                .apiCallTimeout(Duration.ofSeconds(10)).apiCallAttemptTimeout(Duration.ofSeconds(5)));
    }

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    RelayOutboxPort relayOutboxPort(OutboxRepositoryPort outbox, MessagePublisherPort publisher, Clock clock,
                                    OutboxProperties props, PlatformTransactionManager transactionManager) {
        RelayOutboxUseCase useCase = new RelayOutboxUseCase(outbox, publisher, clock, props.batchSize(),
                props.maxAttempts(), props.backoffBase(), props.backoffCap());
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        return () -> tx.execute(status -> useCase.relay());
    }

    @Bean
    PurgeOutboxPort purgeOutboxPort(OutboxRepositoryPort outbox, Clock clock, OutboxProperties props,
                                    PlatformTransactionManager transactionManager) {
        PurgeSentOutboxUseCase useCase = new PurgeSentOutboxUseCase(outbox, clock, props.retention());
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        return () -> tx.execute(status -> useCase.purge());
    }
}
