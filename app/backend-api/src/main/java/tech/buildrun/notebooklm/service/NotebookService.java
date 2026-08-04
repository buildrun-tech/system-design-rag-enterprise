package tech.buildrun.notebooklm.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tech.buildrun.notebooklm.dto.NotebookDetailResponse;
import tech.buildrun.notebooklm.dto.NotebookPatchRequest;
import tech.buildrun.notebooklm.dto.NotebookRequest;
import tech.buildrun.notebooklm.dto.NotebookResponse;
import tech.buildrun.notebooklm.dto.SourceResponse;
import tech.buildrun.notebooklm.entity.Notebook;
import tech.buildrun.notebooklm.entity.User;
import tech.buildrun.notebooklm.exception.NotebookNotFoundException;
import tech.buildrun.notebooklm.repository.NotebookRepository;
import tech.buildrun.notebooklm.repository.SourceRepository;

import java.util.List;
import java.util.UUID;

@Service
public class NotebookService {

    private final NotebookRepository notebookRepository;
    private final SourceRepository sourceRepository;

    public NotebookService(NotebookRepository notebookRepository, SourceRepository sourceRepository) {
        this.notebookRepository = notebookRepository;
        this.sourceRepository = sourceRepository;
    }

    @Transactional
    public NotebookResponse create(User owner, NotebookRequest request) {
        Notebook notebook = new Notebook(owner, request.name(), request.description());
        return NotebookResponse.from(notebookRepository.save(notebook));
    }

    public Page<NotebookResponse> listByOwner(UUID ownerId, Pageable pageable) {
        return notebookRepository.findByOwner_Id(ownerId, pageable).map(NotebookResponse::from);
    }

    public NotebookDetailResponse getDetailOrThrow(UUID notebookId, UUID ownerId) {
        Notebook notebook = getOwnedOrThrow(notebookId, ownerId);
        List<SourceResponse> sources = sourceRepository.findByNotebook_Id(notebookId).stream()
                .map(SourceResponse::from)
                .toList();
        return NotebookDetailResponse.from(notebook, sources);
    }

    @Transactional
    public NotebookResponse update(UUID notebookId, UUID ownerId, NotebookPatchRequest request) {
        Notebook notebook = getOwnedOrThrow(notebookId, ownerId);
        if (request.name() != null) {
            notebook.setName(request.name());
        }
        if (request.description() != null) {
            notebook.setDescription(request.description());
        }
        return NotebookResponse.from(notebook);
    }

    @Transactional
    public void delete(UUID notebookId, UUID ownerId) {
        Notebook notebook = getOwnedOrThrow(notebookId, ownerId);
        notebookRepository.delete(notebook);
    }

    Notebook getOwnedOrThrow(UUID notebookId, UUID ownerId) {
        return notebookRepository.findByIdAndOwner_Id(notebookId, ownerId)
                .orElseThrow(NotebookNotFoundException::new);
    }
}
