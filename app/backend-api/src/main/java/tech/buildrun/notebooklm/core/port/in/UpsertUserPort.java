package tech.buildrun.notebooklm.core.port.in;

import tech.buildrun.notebooklm.core.domain.user.User;

public interface UpsertUserPort {

    User execute(String cognitoSub, String email, String name);
}
