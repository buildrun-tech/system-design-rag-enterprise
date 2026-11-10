package tech.buildrun.notebooklm.core.domain.source;

import java.util.Map;

public record DocumentChunk(String text, Map<String, Object> metadata) {
}
