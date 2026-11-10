package tech.buildrun.notebooklm.core.usecase;

import tech.buildrun.notebooklm.core.domain.exception.UserConflictException;
import tech.buildrun.notebooklm.core.domain.user.User;
import tech.buildrun.notebooklm.core.port.in.UpsertUserPort;
import tech.buildrun.notebooklm.core.port.out.persistence.UserRepositoryPort;

import java.util.Optional;

public class UpsertUserUseCase implements UpsertUserPort {

    private final UserRepositoryPort users;

    public UpsertUserUseCase(UserRepositoryPort users) {
        this.users = users;
    }

    @Override
    public User execute(String cognitoSub, String email, String name) {
        return users.findByCognitoSub(cognitoSub)
                .orElseGet(() -> createOrRecover(cognitoSub, email, name));
    }

    private User createOrRecover(String cognitoSub, String email, String name) {
        try {
            return users.save(User.create(cognitoSub, email, name));
        } catch (UserConflictException e) {
            return users.findByCognitoSub(cognitoSub)
                    .or(() -> reattachByEmail(cognitoSub, email))
                    .orElseThrow(() -> e);
        }
    }

    private Optional<User> reattachByEmail(String cognitoSub, String email) {
        return users.findByEmail(email.toLowerCase()).map(user -> {
            user.reattachTo(cognitoSub);
            return users.save(user);
        });
    }
}
