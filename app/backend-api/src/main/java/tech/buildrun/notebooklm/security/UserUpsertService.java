package tech.buildrun.notebooklm.security;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import tech.buildrun.notebooklm.entity.User;
import tech.buildrun.notebooklm.repository.UserRepository;

@Service
public class UserUpsertService {

    private final UserRepository userRepository;

    public UserUpsertService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public User resolve(String cognitoSub, String email, String name) {
        return userRepository.findByCognitoSub(cognitoSub)
                .orElseGet(() -> createOrRecover(cognitoSub, email, name));
    }

    private User createOrRecover(String cognitoSub, String email, String name) {
        try {
            return userRepository.save(new User(cognitoSub, email, name));
        } catch (DataIntegrityViolationException e) {
            return userRepository.findByCognitoSub(cognitoSub)
                    .orElseThrow(() -> e);
        }
    }
}
