package tech.buildrun.notebooklm.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import tech.buildrun.notebooklm.entity.SourceChunk;

import java.util.UUID;

public interface SourceChunkRepository extends JpaRepository<SourceChunk, UUID> {
}
