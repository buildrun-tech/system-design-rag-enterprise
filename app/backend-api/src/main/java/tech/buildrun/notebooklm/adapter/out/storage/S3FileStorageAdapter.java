package tech.buildrun.notebooklm.adapter.out.storage;

import tech.buildrun.notebooklm.adapter.out.ExternalCallLog;
import io.awspring.cloud.s3.S3Template;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tech.buildrun.notebooklm.core.port.out.FileStoragePort;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;

@Component
public class S3FileStorageAdapter implements FileStoragePort {

    private final S3Template s3Template;
    private final String bucketName;

    public S3FileStorageAdapter(S3Template s3Template, @Value("${aws.s3.bucket-name}") String bucketName) {
        this.s3Template = s3Template;
        this.bucketName = bucketName;
    }

    @Override
    public void store(String key, InputStream content) {
        ExternalCallLog.run("s3", "upload", () -> s3Template.upload(bucketName, key, content, null));
    }

    @Override
    public InputStream download(String key) {
        return ExternalCallLog.measure("s3", "download", () -> {
            try {
                return s3Template.download(bucketName, key).getInputStream();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }

    @Override
    public void delete(String key) {
        ExternalCallLog.run("s3", "delete", () -> s3Template.deleteObject(bucketName, key));
    }
}
