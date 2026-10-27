package tech.buildrun.notebooklm.dto;

import java.util.List;

/** Schema de saída estruturada pedido ao LLM. */
public record QuizOutput(List<QuizQuestion> questions) {
}
