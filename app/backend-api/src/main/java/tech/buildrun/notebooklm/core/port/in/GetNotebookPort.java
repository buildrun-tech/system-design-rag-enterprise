package tech.buildrun.notebooklm.core.port.in;

import tech.buildrun.notebooklm.core.domain.notebook.Notebook;
import tech.buildrun.notebooklm.core.domain.source.Source;

import java.util.List;
import java.util.UUID;

public interface GetNotebookPort {

    NotebookDetail execute(UUID notebookId, UUID ownerId);

    record NotebookDetail(Notebook notebook, List<Source> sources) {
    }
}
