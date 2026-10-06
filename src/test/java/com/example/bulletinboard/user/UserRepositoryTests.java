package com.example.bulletinboard.user;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
class UserRepositoryTests {

    @Autowired
    private UserRepository repository;

    @Test
    void findsUserByEmail() {
        repository.saveAndFlush(new User("alice@example.com", "hash"));

        assertThat(repository.findByEmail("alice@example.com")).hasValueSatisfying(user -> {
            assertThat(user.getId()).isNotNull();
            assertThat(user.getCreatedAt()).isNotNull();
        });
        assertThat(repository.findByEmail("bob@example.com")).isEmpty();
    }

    @Test
    void rejectsDuplicateEmail() {
        repository.saveAndFlush(new User("alice@example.com", "hash"));

        assertThatThrownBy(() -> repository.saveAndFlush(new User("alice@example.com", "other")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
