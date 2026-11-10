package tech.buildrun.notebooklm.core.port.in;

import tech.buildrun.notebooklm.core.domain.source.Source;

public interface UploadSourcePort {

    Source upload(UploadSourceCommand command);
}
