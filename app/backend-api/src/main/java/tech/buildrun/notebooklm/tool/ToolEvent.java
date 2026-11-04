package tech.buildrun.notebooklm.tool;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Ciclo de vida de uma execução de tool, enviado ao cliente no stream SSE.
 * {@code start} traz label e input; {@code end}/{@code error} trazem result (no erro, a mensagem) e duração.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ToolEvent(String id, String name, String label, Status status,
                        String input, String result, Long durationMs) {

    public enum Status { start, end, error }
}
