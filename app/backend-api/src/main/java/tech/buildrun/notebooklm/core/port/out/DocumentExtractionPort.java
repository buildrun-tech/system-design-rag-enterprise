package tech.buildrun.notebooklm.core.port.out;

import tech.buildrun.notebooklm.core.domain.source.DocumentChunk;

import java.io.InputStream;
import java.util.List;

public interface DocumentExtractionPort {

    List<DocumentChunk> extractAndSplit(InputStream content, String filename);
}
