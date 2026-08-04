package tech.buildrun.notebooklm.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import tech.buildrun.notebooklm.entity.User;
import tech.buildrun.notebooklm.repository.UserRepository;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserUpsertServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserUpsertService userUpsertService;

    @Test
    void reusesExistingUserWithoutSaving() {
        User existing = new User("sub-1", "a@test.com", "A");
        when(userRepository.findByCognitoSub("sub-1")).thenReturn(Optional.of(existing));

        User result = userUpsertService.resolve("sub-1", "a@test.com", "A");

        assertThat(result).isSameAs(existing);
        verify(userRepository, never()).save(any());
    }

    @Test
    void createsUserWhenNotFound() {
        User created = new User("sub-2", "b@test.com", "B");
        when(userRepository.findByCognitoSub("sub-2")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenReturn(created);

        User result = userUpsertService.resolve("sub-2", "b@test.com", "B");

        assertThat(result).isSameAs(created);
    }

    @Test
    void recoversFromConcurrentInsertRace() {
        User winner = new User("sub-3", "c@test.com", "C");
        when(userRepository.findByCognitoSub("sub-3"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(winner));
        when(userRepository.save(any(User.class))).thenThrow(new DataIntegrityViolationException("duplicate"));

        User result = userUpsertService.resolve("sub-3", "c@test.com", "C");

        assertThat(result).isSameAs(winner);
        verify(userRepository, times(2)).findByCognitoSub("sub-3");
    }
}
