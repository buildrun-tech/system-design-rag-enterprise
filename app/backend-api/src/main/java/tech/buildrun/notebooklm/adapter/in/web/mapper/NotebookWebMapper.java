package tech.buildrun.notebooklm.adapter.in.web.mapper;

import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import tech.buildrun.notebooklm.adapter.in.web.dto.NotebookDetailResponse;
import tech.buildrun.notebooklm.adapter.in.web.dto.NotebookResponse;
import tech.buildrun.notebooklm.core.domain.notebook.Notebook;
import tech.buildrun.notebooklm.core.domain.shared.PageResult;
import tech.buildrun.notebooklm.core.domain.source.Source;
import tech.buildrun.notebooklm.core.port.in.GetNotebookPort.NotebookDetail;

public final class NotebookWebMapper {

    private NotebookWebMapper() {
    }

    public static NotebookResponse toResponse(Notebook notebook) {
        return new NotebookResponse(notebook.id(), notebook.name(), notebook.description(),
                notebook.createdAt(), notebook.updatedAt());
    }

    public static PageImpl<NotebookResponse> toPage(PageResult<Notebook> result, Pageable pageable) {
        return new PageImpl<>(result.items().stream().map(NotebookWebMapper::toResponse).toList(), pageable,
                result.totalElements());
    }

    public static NotebookDetailResponse toDetailResponse(NotebookDetail detail) {
        Notebook notebook = detail.notebook();
        return new NotebookDetailResponse(notebook.id(), notebook.name(), notebook.description(),
                notebook.createdAt(), notebook.updatedAt(),
                detail.sources().stream().map(NotebookWebMapper::toSourceItem).toList());
    }

    private static NotebookDetailResponse.SourceItem toSourceItem(Source source) {
        return new NotebookDetailResponse.SourceItem(source.id(), source.name(), source.type(), source.status(),
                source.errorMessage(), source.createdAt());
    }
}
