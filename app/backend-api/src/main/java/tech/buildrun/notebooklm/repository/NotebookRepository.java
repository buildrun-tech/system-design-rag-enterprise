package tech.buildrun.notebooklm.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import tech.buildrun.notebooklm.entity.Notebook;

import java.util.UUID;

public interface NotebookRepository extends JpaRepository<Notebook, UUID> {
}
