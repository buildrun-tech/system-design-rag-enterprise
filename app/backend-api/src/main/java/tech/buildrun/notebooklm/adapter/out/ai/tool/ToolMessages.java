package tech.buildrun.notebooklm.adapter.out.ai.tool;

import java.util.Map;
import java.util.Optional;

/** Textos em português, para o usuário final, de cada tool (label, sucesso, erro). */
final class ToolMessages {

    record Texts(String label, String ok, String error) {}

    /** Tabela fixa por nome da tool; migrar para anotação customizada quando houver várias tools. */
    private static final Map<String, Texts> TEXTS = Map.of(
            "currentDateTime", new Texts("Consultando data e hora", "Data e hora consultadas",
                    "Não foi possível consultar data e hora"));

    private ToolMessages() {
    }

    static Optional<Texts> forTool(String name) {
        return Optional.ofNullable(TEXTS.get(name));
    }
}
