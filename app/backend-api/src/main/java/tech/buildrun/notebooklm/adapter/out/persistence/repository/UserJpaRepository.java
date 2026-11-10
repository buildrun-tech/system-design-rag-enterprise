package tech.buildrun.notebooklm.adapter.out.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import tech.buildrun.notebooklm.adapter.out.persistence.entity.UserJpaEntity;

import java.util.Optional;
import java.util.UUID;

public interface UserJpaRepository extends JpaRepository<UserJpaEntity, UUID> {

    Optional<UserJpaEntity> findByCognitoSub(String cognitoSub);

    Optional<UserJpaEntity> findByEmail(String email);
}
