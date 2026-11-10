package tech.buildrun.notebooklm.core.domain.source;

import org.junit.jupiter.api.Test;
import tech.buildrun.notebooklm.core.domain.exception.InvalidStateTransitionException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SourceTest {

    private Source pending() {
        return Source.create(UUID.randomUUID(), "doc.pdf", SourceType.FILE, null, null);
    }

    @Test
    void createStartsPendingWithoutError() {
        Source source = pending();

        assertThat(source.status()).isEqualTo(SourceStatus.PENDING);
        assertThat(source.errorMessage()).isNull();
        assertThat(source.name()).isEqualTo("doc.pdf");
        assertThat(source.type()).isEqualTo(SourceType.FILE);
    }

    @Test
    void storedAtSetsS3Key() {
        Source source = pending();

        source.storedAt("a/b");

        assertThat(source.s3Key()).isEqualTo("a/b");
    }

    @Test
    void followsPendingProcessingReady() {
        Source source = pending();

        source.markProcessing();
        assertThat(source.status()).isEqualTo(SourceStatus.PROCESSING);
        source.markReady();

        assertThat(source.isReady()).isTrue();
    }

    @Test
    void markReadyRequiresProcessing() {
        assertThatThrownBy(() -> pending().markReady()).isInstanceOf(InvalidStateTransitionException.class);
    }

    @Test
    void readyCannotGoBackToProcessing() {
        Source source = pending();
        source.markProcessing();
        source.markReady();

        assertThatThrownBy(source::markProcessing).isInstanceOf(InvalidStateTransitionException.class);
    }

    @Test
    void markFailedRecordsReason() {
        Source source = pending();
        source.markProcessing();

        source.markFailed("boom");

        assertThat(source.status()).isEqualTo(SourceStatus.FAILED);
        assertThat(source.errorMessage()).isEqualTo("boom");
    }

    @Test
    void markFailedNeverRegressesReady() {
        Source source = pending();
        source.markProcessing();
        source.markReady();

        source.markFailed("late duplicate");

        assertThat(source.status()).isEqualTo(SourceStatus.READY);
        assertThat(source.errorMessage()).isNull();
    }

    @Test
    void belongsToChecksNotebook() {
        UUID notebookId = UUID.randomUUID();
        Source source = Source.create(notebookId, "n", SourceType.URL, null, "https://x");

        assertThat(source.belongsTo(notebookId)).isTrue();
        assertThat(source.belongsTo(UUID.randomUUID())).isFalse();
    }
}
