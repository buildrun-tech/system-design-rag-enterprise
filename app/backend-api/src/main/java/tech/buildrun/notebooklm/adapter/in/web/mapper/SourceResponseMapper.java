package tech.buildrun.notebooklm.adapter.in.web.mapper;

import org.springframework.web.multipart.MultipartFile;
import tech.buildrun.notebooklm.adapter.in.web.dto.SourceResponse;
import tech.buildrun.notebooklm.core.domain.source.Source;
import tech.buildrun.notebooklm.core.port.in.UploadSourceCommand;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.UUID;

public final class SourceResponseMapper {

    private SourceResponseMapper() {
    }

    public static SourceResponse toResponse(Source source) {
        return new SourceResponse(source.id(), source.name(), source.type(), source.status(),
                source.errorMessage(), source.createdAt());
    }

    public static UploadSourceCommand toCommand(UUID notebookId, UUID ownerId, MultipartFile file) {
        try {
            return new UploadSourceCommand(notebookId, ownerId, file.getOriginalFilename(), file.getContentType(),
                    file.getSize(), file.getInputStream());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
