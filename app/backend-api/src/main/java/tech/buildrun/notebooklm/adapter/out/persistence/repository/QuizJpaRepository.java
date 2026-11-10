package tech.buildrun.notebooklm.adapter.out.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import tech.buildrun.notebooklm.adapter.out.persistence.entity.QuizJpaEntity;

import java.util.Optional;
import java.util.UUID;

public interface QuizJpaRepository extends JpaRepository<QuizJpaEntity, UUID> {

    Optional<QuizJpaEntity> findByIdAndConversationId(UUID id, UUID conversationId);
}
