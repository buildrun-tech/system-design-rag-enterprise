package tech.buildrun.notebooklm.service;

import tech.buildrun.notebooklm.entity.Conversation;
import tech.buildrun.notebooklm.entity.Source;
import tech.buildrun.notebooklm.entity.SourceStatus;
import tech.buildrun.notebooklm.repository.SourceRepository;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/** Sources consideradas pelo RAG e pelo quiz: as ativas da conversation, ou todas READY do notebook. */
final class ActiveSources {

    private ActiveSources() {
    }

    static List<UUID> resolveIds(Conversation conversation, SourceRepository sourceRepository) {
        var activeSources = conversation.getActiveSources();
        return activeSources.isEmpty()
                ? sourceRepository.findByNotebookIdAndStatus(conversation.getNotebook().getId(), SourceStatus.READY)
                        .stream().map(Source::getId).toList()
                : activeSources.stream().map(Source::getId).toList();
    }

    static String filterExpression(List<UUID> sourceIds) {
        if (sourceIds.isEmpty()) {
            return "source_id == 'none'";
        }
        String ids = sourceIds.stream().map(id -> "'" + id + "'").collect(Collectors.joining(","));
        return "source_id in [" + ids + "]";
    }
}
