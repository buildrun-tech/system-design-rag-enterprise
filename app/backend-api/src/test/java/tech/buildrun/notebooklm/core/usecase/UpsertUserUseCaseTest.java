package tech.buildrun.notebooklm.core.usecase;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tech.buildrun.notebooklm.core.domain.exception.UserConflictException;
import tech.buildrun.notebooklm.core.domain.user.User;
import tech.buildrun.notebooklm.core.port.out.persistence.UserRepositoryPort;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UpsertUserUseCaseTest {

    @Mock
    private UserRepositoryPort userRepository;

    @InjectMocks
    private UpsertUserUseCase upsertUser;

    @Test
    void reusesExistingUserWithoutSaving() {
        User existing = User.create("sub-1", "a@test.com", "A");
        when(userRepository.findByCognitoSub("sub-1")).thenReturn(Optional.of(existing));

        User result = upsertUser.execute("sub-1", "a@test.com", "A");

        assertThat(result).isSameAs(existing);
        verify(userRepository, never()).save(any());
    }

    @Test
    void createsUserWhenNotFound() {
        User created = User.create("sub-2", "b@test.com", "B");
        when(userRepository.findByCognitoSub("sub-2")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenReturn(created);

        assertThat(upsertUser.execute("sub-2", "b@test.com", "B")).isSameAs(created);
    }

    @Test
    void recoversFromConcurrentInsertRace() {
        User winner = User.create("sub-3", "c@test.com", "C");
        when(userRepository.findByCognitoSub("sub-3"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(winner));
        when(userRepository.save(any(User.class))).thenThrow(new UserConflictException(null));

        assertThat(upsertUser.execute("sub-3", "c@test.com", "C")).isSameAs(winner);
        verify(userRepository, times(2)).findByCognitoSub("sub-3");
    }

    @Test
    void reattachesExistingUserWhenEmailBelongsToStaleCognitoSub() {
        User existing = User.create("old-sub", "taken@test.com", "D");
        when(userRepository.findByCognitoSub("sub-4")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenThrow(new UserConflictException(null));
        when(userRepository.findByEmail("taken@test.com")).thenReturn(Optional.of(existing));
        doReturn(existing).when(userRepository).save(existing);

        User result = upsertUser.execute("sub-4", "taken@test.com", "D");

        assertThat(result).isSameAs(existing);
        assertThat(existing.cognitoSub()).isEqualTo("sub-4");
    }

    @Test
    void propagatesExceptionWhenNoUserMatchesCognitoSubOrEmail() {
        UserConflictException conflict = new UserConflictException(null);
        when(userRepository.findByCognitoSub("sub-5")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenThrow(conflict);
        when(userRepository.findByEmail("e@test.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> upsertUser.execute("sub-5", "e@test.com", "E")).isSameAs(conflict);
    }
}
