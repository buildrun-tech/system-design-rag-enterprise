package tech.buildrun.notebooklm.adapter.out.persistence;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import tech.buildrun.notebooklm.adapter.out.persistence.mapper.UserMapper;
import tech.buildrun.notebooklm.adapter.out.persistence.repository.UserJpaRepository;
import tech.buildrun.notebooklm.core.domain.exception.UserConflictException;
import tech.buildrun.notebooklm.core.domain.user.User;
import tech.buildrun.notebooklm.core.port.out.persistence.UserRepositoryPort;

import java.util.Optional;

@Component
public class UserRepositoryAdapter implements UserRepositoryPort {

    private final UserJpaRepository repository;

    public UserRepositoryAdapter(UserJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<User> findByCognitoSub(String cognitoSub) {
        return repository.findByCognitoSub(cognitoSub).map(UserMapper::toDomain);
    }

    @Override
    public Optional<User> findByEmail(String email) {
        return repository.findByEmail(email).map(UserMapper::toDomain);
    }

    @Override
    public User save(User user) {
        try {
            return UserMapper.toDomain(repository.save(UserMapper.toEntity(user)));
        } catch (DataIntegrityViolationException e) {
            throw new UserConflictException(e);
        }
    }
}
