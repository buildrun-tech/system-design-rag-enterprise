package tech.buildrun.notebooklm.adapter.out;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;

import java.util.function.Supplier;

/** Um evento por chamada a sistema externo: integration, operation, outcome, durationMs (causa em stack_trace). */
public final class ExternalCallLog {

    private static final Logger log = LoggerFactory.getLogger(ExternalCallLog.class);

    private ExternalCallLog() {
    }

    public static <T> T measure(String integration, String operation, Supplier<T> call) {
        long start = System.nanoTime();
        try {
            T result = call.get();
            record(integration, operation, start, null);
            return result;
        } catch (RuntimeException | Error e) {
            record(integration, operation, start, e);
            throw e;
        }
    }

    public static void run(String integration, String operation, Runnable call) {
        measure(integration, operation, () -> {
            call.run();
            return null;
        });
    }

    public static void record(String integration, String operation, long startNanos, Throwable error) {
        var event = log.atLevel(error == null ? Level.INFO : Level.ERROR).setMessage("External call")
                .addKeyValue("integration", integration).addKeyValue("operation", operation)
                .addKeyValue("outcome", error == null ? "ok" : "error")
                .addKeyValue("durationMs", (System.nanoTime() - startNanos) / 1_000_000);
        (error == null ? event : event.setCause(error)).log();
    }
}
