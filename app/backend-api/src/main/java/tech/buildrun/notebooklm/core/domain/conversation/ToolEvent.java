package tech.buildrun.notebooklm.core.domain.conversation;

public record ToolEvent(String id, String name, String label, Status status,
                        String input, String result, Long durationMs, String summary) {

    public enum Status { start, end, error }
}
