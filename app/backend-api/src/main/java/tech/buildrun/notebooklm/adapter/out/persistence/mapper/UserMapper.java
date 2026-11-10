package tech.buildrun.notebooklm.adapter.out.persistence.mapper;

import tech.buildrun.notebooklm.adapter.out.persistence.entity.UserJpaEntity;
import tech.buildrun.notebooklm.core.domain.user.User;

public final class UserMapper {

    private UserMapper() {
    }

    public static User toDomain(UserJpaEntity entity) {
        return User.restore(entity.getId(), entity.getCognitoSub(), entity.getEmail(), entity.getName(),
                entity.getCreatedAt());
    }

    public static UserJpaEntity toEntity(User user) {
        return new UserJpaEntity(user.id(), user.cognitoSub(), user.email(), user.name(), user.createdAt());
    }
}
