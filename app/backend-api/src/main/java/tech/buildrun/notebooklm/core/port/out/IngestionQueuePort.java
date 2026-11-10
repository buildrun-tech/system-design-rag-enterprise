package tech.buildrun.notebooklm.core.port.out;

import tech.buildrun.notebooklm.core.domain.source.IngestionRequest;

public interface IngestionQueuePort {

    void enqueue(IngestionRequest request);
}
