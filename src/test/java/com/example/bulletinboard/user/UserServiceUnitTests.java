package com.example.bulletinboard.user;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UserServiceUnitTests {

    @Test
    void mapsConcurrentDuplicateInsertToConflict() {
        UserRepository repository = mock(UserRepository.class);
        when(repository.findByEmail("alice@example.com")).thenReturn(Optional.empty());
        when(repository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("unique email"));
        var service = new UserService(repository, PasswordEncoderFactories.createDelegatingPasswordEncoder());

        assertThatThrownBy(() -> service.register(new RegisterRequest("alice@example.com", "correct-horse")))
                .isInstanceOf(EmailAlreadyRegisteredException.class)
                .hasCauseInstanceOf(DataIntegrityViolationException.class);
    }
}
