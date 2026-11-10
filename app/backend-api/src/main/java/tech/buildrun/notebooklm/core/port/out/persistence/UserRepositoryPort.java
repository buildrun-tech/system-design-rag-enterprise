package tech.buildrun.notebooklm.core.port.out.persistence;

import tech.buildrun.notebooklm.core.domain.user.User;

import java.util.Optional;

public interface UserRepositoryPort {

    Optional<User> findByCognitoSub(String cognitoSub);

    Optional<User> findByEmail(String email);

    User save(User user);
}
