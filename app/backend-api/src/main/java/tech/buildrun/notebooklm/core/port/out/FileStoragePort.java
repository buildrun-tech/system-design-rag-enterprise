package tech.buildrun.notebooklm.core.port.out;

import java.io.InputStream;

public interface FileStoragePort {

    void store(String key, InputStream content);

    InputStream download(String key);

    void delete(String key);
}
