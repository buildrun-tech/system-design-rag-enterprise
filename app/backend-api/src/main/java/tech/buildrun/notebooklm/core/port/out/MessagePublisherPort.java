package tech.buildrun.notebooklm.core.port.out;

import tech.buildrun.notebooklm.core.domain.outbox.OutboxEvent;

public interface MessagePublisherPort {

    void publish(OutboxEvent event);
}
