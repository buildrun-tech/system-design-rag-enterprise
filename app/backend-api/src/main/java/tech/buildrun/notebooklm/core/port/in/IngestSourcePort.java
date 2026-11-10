package tech.buildrun.notebooklm.core.port.in;

import java.util.UUID;

public interface IngestSourcePort {

    void ingest(UUID sourceId, Runnable onProgress);
}
