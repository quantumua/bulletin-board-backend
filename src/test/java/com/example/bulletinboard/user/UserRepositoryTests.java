package com.example.bulletinboard.user;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class UserRepositoryTests {

    @Autowired
    private UserRepository repository;

    @BeforeEach
    void cleanDatabase() {
        repository.deleteAll();
    }

    @Test
    void rejectsUsernameDifferingOnlyByCase() {
        repository.saveAndFlush(new AppUser("Alice", "hash"));

        assertThatThrownBy(() -> repository.saveAndFlush(new AppUser("aLICE", "hash")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void findsUserIgnoringCaseAndKeepsOriginalSpelling() {
        repository.saveAndFlush(new AppUser("Alice", "hash"));

        assertThat(repository.findByUsernameLower(AppUser.normalize("ALICE")))
                .get().extracting(AppUser::getUsername).isEqualTo("Alice");
    }
}
