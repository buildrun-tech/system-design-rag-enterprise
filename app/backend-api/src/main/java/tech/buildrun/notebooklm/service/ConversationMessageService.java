package tech.buildrun.notebooklm.service;

import org.springframework.stereotype.Service;
import tech.buildrun.notebooklm.dto.ConversationMessageResponse;
import tech.buildrun.notebooklm.exception.ConversationNotFoundException;
import tech.buildrun.notebooklm.repository.ConversationMessageRepository;
import tech.buildrun.notebooklm.repository.ConversationRepository;

import java.util.List;
import java.util.UUID;

@Service
public class ConversationMessageService {

    private final ConversationRepository conversationRepository;
    private final ConversationMessageRepository conversationMessageRepository;

    public ConversationMessageService(ConversationRepository conversationRepository,
                                       ConversationMessageRepository conversationMessageRepository) {
        this.conversationRepository = conversationRepository;
        this.conversationMessageRepository = conversationMessageRepository;
    }

    public List<ConversationMessageResponse> listByConversation(UUID conversationId, UUID ownerId) {
        conversationRepository.findByIdAndNotebook_Owner_Id(conversationId, ownerId)
                .orElseThrow(ConversationNotFoundException::new);
        return conversationMessageRepository.findAllByConversationIdAndOwnerId(conversationId, ownerId).stream()
                .map(ConversationMessageResponse::from)
                .toList();
    }
}
