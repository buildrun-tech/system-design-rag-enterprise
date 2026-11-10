package tech.buildrun.notebooklm.core.port.out.async;

public interface BackgroundTaskPort {

    void submit(Runnable task);
}
