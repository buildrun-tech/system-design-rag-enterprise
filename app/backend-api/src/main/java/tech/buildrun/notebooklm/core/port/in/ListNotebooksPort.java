package tech.buildrun.notebooklm.core.port.in;

import tech.buildrun.notebooklm.core.domain.notebook.Notebook;
import tech.buildrun.notebooklm.core.domain.shared.PageQuery;
import tech.buildrun.notebooklm.core.domain.shared.PageResult;

import java.util.UUID;

public interface ListNotebooksPort {

    PageResult<Notebook> execute(UUID ownerId, PageQuery pageQuery);
}
