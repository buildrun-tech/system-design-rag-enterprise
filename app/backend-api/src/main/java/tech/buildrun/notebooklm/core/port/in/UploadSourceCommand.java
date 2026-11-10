package tech.buildrun.notebooklm.core.port.in;

import java.io.InputStream;
import java.util.UUID;

public record UploadSourceCommand(UUID notebookId, UUID ownerId, String filename, String contentType, long size,
                                  InputStream content) {
}
