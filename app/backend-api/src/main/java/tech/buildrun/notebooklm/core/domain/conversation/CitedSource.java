package tech.buildrun.notebooklm.core.domain.conversation;

import java.util.List;
import java.util.UUID;

public record CitedSource(UUID sourceId, String name, List<CitedChunk> chunks) {
}
