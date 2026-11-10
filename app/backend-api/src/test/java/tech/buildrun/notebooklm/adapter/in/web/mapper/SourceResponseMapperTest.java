package tech.buildrun.notebooklm.adapter.in.web.mapper;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import tech.buildrun.notebooklm.core.domain.source.Source;
import tech.buildrun.notebooklm.core.domain.source.SourceStatus;
import tech.buildrun.notebooklm.core.domain.source.SourceType;
import tech.buildrun.notebooklm.core.port.in.UploadSourceCommand;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SourceResponseMapperTest {

    @Test
    void mapsSourceToResponse() {
        Instant now = Instant.now();
        Source source = Source.restore(UUID.randomUUID(), UUID.randomUUID(), "d.pdf", SourceType.FILE, "k", null,
                SourceStatus.FAILED, "err", now);

        var response = SourceResponseMapper.toResponse(source);

        assertThat(response.id()).isEqualTo(source.id());
        assertThat(response.name()).isEqualTo("d.pdf");
        assertThat(response.type()).isEqualTo(SourceType.FILE);
        assertThat(response.status()).isEqualTo(SourceStatus.FAILED);
        assertThat(response.errorMessage()).isEqualTo("err");
        assertThat(response.createdAt()).isEqualTo(now);
    }

    @Test
    void mapsMultipartToCommand() throws Exception {
        UUID notebookId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        var file = new MockMultipartFile("file", "doc.pdf", "application/pdf", "abc".getBytes());

        UploadSourceCommand command = SourceResponseMapper.toCommand(notebookId, ownerId, file);

        assertThat(command.filename()).isEqualTo("doc.pdf");
        assertThat(command.contentType()).isEqualTo("application/pdf");
        assertThat(command.size()).isEqualTo(3);
        assertThat(command.content().readAllBytes()).isEqualTo("abc".getBytes());
    }
}
