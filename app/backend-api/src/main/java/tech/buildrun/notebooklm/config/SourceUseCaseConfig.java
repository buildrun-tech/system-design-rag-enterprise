package tech.buildrun.notebooklm.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tech.buildrun.notebooklm.core.port.in.DeleteSourcePort;
import tech.buildrun.notebooklm.core.port.in.GetSourcePort;
import tech.buildrun.notebooklm.core.port.in.IngestSourcePort;
import tech.buildrun.notebooklm.core.port.in.ListSourcesPort;
import tech.buildrun.notebooklm.core.port.in.UploadSourcePort;
import tech.buildrun.notebooklm.core.port.out.DocumentEnrichmentPort;
import tech.buildrun.notebooklm.core.port.out.ChunkStorePort;
import tech.buildrun.notebooklm.core.port.out.DocumentExtractionPort;
import tech.buildrun.notebooklm.core.port.out.FileStoragePort;
import tech.buildrun.notebooklm.core.port.out.IngestionQueuePort;
import tech.buildrun.notebooklm.core.port.out.persistence.NotebookRepositoryPort;
import tech.buildrun.notebooklm.core.port.out.persistence.SourceRepositoryPort;
import tech.buildrun.notebooklm.core.usecase.DeleteSourceUseCase;
import tech.buildrun.notebooklm.core.usecase.GetSourceUseCase;
import tech.buildrun.notebooklm.core.usecase.IngestSourceUseCase;
import tech.buildrun.notebooklm.core.usecase.ListSourcesUseCase;
import tech.buildrun.notebooklm.core.usecase.UploadSourceUseCase;

@Configuration
public class SourceUseCaseConfig {

    @Bean
    UploadSourcePort uploadSourcePort(SourceRepositoryPort sources, NotebookRepositoryPort notebooks,
                                      FileStoragePort storage, IngestionQueuePort queue,
                                      PlatformTransactionManager transactionManager) {
        UploadSourceUseCase useCase = new UploadSourceUseCase(sources, notebooks, storage, queue);
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        return command -> tx.execute(status -> useCase.upload(command));
    }

    @Bean
    ListSourcesPort listSourcesPort(SourceRepositoryPort sources, NotebookRepositoryPort notebooks) {
        return new ListSourcesUseCase(sources, notebooks);
    }

    @Bean
    GetSourcePort getSourcePort(SourceRepositoryPort sources, NotebookRepositoryPort notebooks) {
        return new GetSourceUseCase(sources, notebooks);
    }

    @Bean
    DeleteSourcePort deleteSourcePort(GetSourcePort getSource, SourceRepositoryPort sources, FileStoragePort storage,
                                      ChunkStorePort chunks, PlatformTransactionManager transactionManager) {
        DeleteSourceUseCase useCase = new DeleteSourceUseCase(getSource, sources, storage, chunks);
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        return (notebookId, sourceId, ownerId) ->
                tx.executeWithoutResult(status -> useCase.delete(notebookId, sourceId, ownerId));
    }

    @Bean
    IngestSourcePort ingestSourcePort(SourceRepositoryPort sources, FileStoragePort storage,
                                      DocumentExtractionPort extraction, DocumentEnrichmentPort enrichment,
                                      ChunkStorePort chunks) {
        return new IngestSourceUseCase(sources, storage, extraction, enrichment, chunks);
    }
}
