package tech.buildrun.notebooklm.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import tech.buildrun.notebooklm.entity.User;

import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {
}
