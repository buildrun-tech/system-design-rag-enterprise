package tech.buildrun.notebooklm.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import tech.buildrun.notebooklm.entity.Source;

import java.util.UUID;

public interface SourceRepository extends JpaRepository<Source, UUID> {
}
