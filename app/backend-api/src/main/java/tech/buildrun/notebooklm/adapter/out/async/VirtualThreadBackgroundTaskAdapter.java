package tech.buildrun.notebooklm.adapter.out.async;

import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import tech.buildrun.notebooklm.core.port.out.async.BackgroundTaskPort;

import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

@Component
public class VirtualThreadBackgroundTaskAdapter implements BackgroundTaskPort {

    private final Executor executor;

    public VirtualThreadBackgroundTaskAdapter() {
        this(Executors.newVirtualThreadPerTaskExecutor());
    }

    VirtualThreadBackgroundTaskAdapter(Executor executor) {
        this.executor = executor;
    }

    @Override
    public void submit(Runnable task) {
        Map<String, String> mdc = MDC.getCopyOfContextMap();
        executor.execute(() -> {
            if (mdc != null) MDC.setContextMap(mdc);
            try {
                task.run();
            } finally {
                MDC.clear();
            }
        });
    }
}
