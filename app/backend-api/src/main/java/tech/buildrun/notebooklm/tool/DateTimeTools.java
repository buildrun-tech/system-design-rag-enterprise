package tech.buildrun.notebooklm.tool;

import org.springframework.ai.tool.annotation.Tool;

import java.time.Clock;
import java.time.ZoneId;
import java.time.ZonedDateTime;

public class DateTimeTools {

    private static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");

    private final Clock clock;

    public DateTimeTools() {
        this(Clock.system(ZONE));
    }

    DateTimeTools(Clock clock) {
        this.clock = clock;
    }

    @Tool(description = "Retorna a data e hora atuais (fuso America/Sao_Paulo) em formato ISO-8601. "
            + "Use quando o usuario perguntar a data, o dia ou a hora de agora.")
    public String currentDateTime() {
        return ZonedDateTime.now(clock).toString();
    }
}
