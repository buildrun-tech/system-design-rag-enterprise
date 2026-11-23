package tech.buildrun.notebooklm.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

@ConfigurationProperties("outbox")
public record OutboxProperties(
        @DefaultValue("1s") Duration pollInterval,
        @DefaultValue("1h") Duration purgeInterval,
        @DefaultValue("10") int batchSize,
        @DefaultValue("10") int maxAttempts,
        @DefaultValue("2s") Duration backoffBase,
        @DefaultValue("5m") Duration backoffCap,
        @DefaultValue("7d") Duration retention) {
}
