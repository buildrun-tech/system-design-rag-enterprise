package tech.buildrun.notebooklm.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tech.buildrun.notebooklm.dto.ConversationCreateRequest;
import tech.buildrun.notebooklm.dto.ConversationDetailResponse;
import tech.buildrun.notebooklm.dto.ConversationResponse;
import tech.buildrun.notebooklm.entity.Conversation;
import tech.buildrun.notebooklm.entity.ConversationMessage;
import tech.buildrun.notebooklm.entity.Notebook;
import tech.buildrun.notebooklm.entity.Source;
import tech.buildrun.notebooklm.entity.SourceStatus;
import tech.buildrun.notebooklm.exception.InvalidSourceIdsException;
import tech.buildrun.notebooklm.repository.ConversationMessageRepository;
import tech.buildrun.notebooklm.repository.ConversationRepository;
import tech.buildrun.notebooklm.repository.SourceRepository;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ConversationService {

    private static final int PREVIEW_MAX_LENGTH = 50;

    private final ConversationRepository conversationRepository;
    private final ConversationMessageRepository conversationMessageRepository;
    private final SourceRepository sourceRepository;
    private final NotebookService notebookService;

    public ConversationService(ConversationRepository conversationRepository,
                                ConversationMessageRepository conversationMessageRepository,
                                SourceRepository sourceRepository,
                                NotebookService notebookService) {
        this.conversationRepository = conversationRepository;
        this.conversationMessageRepository = conversationMessageRepository;
        this.sourceRepository = sourceRepository;
        this.notebookService = notebookService;
    }

    @Transactional
    public ConversationDetailResponse create(UUID notebookId, UUID ownerId, ConversationCreateRequest request) {
        Notebook notebook = notebookService.getOwnedOrThrow(notebookId, ownerId);
        Set<Source> activeSources = resolveActiveSources(notebook, request.activeSourceIds());

        Conversation conversation = new Conversation(notebook);
        conversation.getActiveSources().addAll(activeSources);
        conversation = conversationRepository.save(conversation);

        return toDetailResponse(conversation);
    }

    public List<ConversationResponse> listByNotebook(UUID notebookId, UUID ownerId) {
        notebookService.getOwnedOrThrow(notebookId, ownerId);
        return conversationRepository.findByNotebook_IdAndNotebook_Owner_IdOrderByCreatedAtDesc(notebookId, ownerId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    private Set<Source> resolveActiveSources(Notebook notebook, List<UUID> requestedSourceIds) {
        if (requestedSourceIds == null || requestedSourceIds.isEmpty()) {
            return Set.copyOf(sourceRepository.findByNotebook_IdAndStatus(notebook.getId(), SourceStatus.READY));
        }

        List<Source> notebookSources = sourceRepository.findByNotebook_Id(notebook.getId());
        Set<UUID> notebookSourceIds = notebookSources.stream().map(Source::getId).collect(Collectors.toSet());
        if (!notebookSourceIds.containsAll(requestedSourceIds)) {
            throw new InvalidSourceIdsException();
        }

        Set<UUID> requestedSet = Set.copyOf(requestedSourceIds);
        return notebookSources.stream()
                .filter(source -> requestedSet.contains(source.getId()))
                .collect(Collectors.toSet());
    }

    private ConversationResponse toResponse(Conversation conversation) {
        String preview = conversationMessageRepository.findFirstByConversation_IdOrderByCreatedAtAsc(conversation.getId())
                .map(ConversationMessage::getContent)
                .map(this::truncate)
                .orElse(null);
        return new ConversationResponse(conversation.getId(), conversation.getNotebook().getId(),
                conversation.getCreatedAt(), preview);
    }

    private ConversationDetailResponse toDetailResponse(Conversation conversation) {
        List<UUID> activeSourceIds = conversation.getActiveSources().stream().map(Source::getId).toList();
        return new ConversationDetailResponse(conversation.getId(), conversation.getNotebook().getId(),
                activeSourceIds, conversation.getCreatedAt());
    }

    private String truncate(String content) {
        if (content.length() <= PREVIEW_MAX_LENGTH) {
            return content;
        }
        return content.substring(0, PREVIEW_MAX_LENGTH) + "...";
    }
}
