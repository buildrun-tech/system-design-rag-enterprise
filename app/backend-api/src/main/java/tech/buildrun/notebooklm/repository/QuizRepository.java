package tech.buildrun.notebooklm.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import tech.buildrun.notebooklm.entity.Quiz;

import java.util.Optional;
import java.util.UUID;

public interface QuizRepository extends JpaRepository<Quiz, UUID> {

    Optional<Quiz> findByIdAndConversationId(UUID id, UUID conversationId);
}
