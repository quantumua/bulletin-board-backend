package com.example.bulletinboard.account;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
class UserAccountRepositoryTests {

    private static final Instant NOW = Instant.parse("2026-10-20T10:00:00Z");

    @Autowired
    private UserAccountRepository repository;

    @Test
    void findsAccountByNormalisedEmail() {
        UserAccount saved = repository.saveAndFlush(account("ann@example.com", "Ann"));

        assertThat(repository.findByEmail("ann@example.com"))
                .get()
                .extracting(UserAccount::getId)
                .isEqualTo(saved.getId());
    }

    @Test
    void unknownEmailFindsNothing() {
        repository.saveAndFlush(account("ann@example.com", "Ann"));

        assertThat(repository.findByEmail("nobody@example.com")).isEmpty();
    }

    @Test
    void duplicateEmailViolatesUniqueConstraint() {
        repository.saveAndFlush(account("ann@example.com", "Ann"));

        assertThatThrownBy(() -> repository.saveAndFlush(account("ann@example.com", "Another Ann")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void findAllByIdReturnsNamesForSeveralOwnersInOneCall() {
        UserAccount ann = repository.save(account("ann@example.com", "Ann"));
        UserAccount bob = repository.save(account("bob@example.com", "Bob"));
        repository.save(account("cid@example.com", "Cid"));
        repository.flush();

        Map<Long, String> names = repository.findAllById(List.of(ann.getId(), bob.getId())).stream()
                .collect(Collectors.toMap(UserAccount::getId, UserAccount::getDisplayName));

        assertThat(names).containsOnly(Map.entry(ann.getId(), "Ann"), Map.entry(bob.getId(), "Bob"));
    }

    @Test
    void lockedReadReturnsTheRow() {
        UserAccount saved = repository.saveAndFlush(account("ann@example.com", "Ann"));

        assertThat(repository.findByEmailForUpdate("ann@example.com"))
                .get()
                .extracting(UserAccount::getId)
                .isEqualTo(saved.getId());
        assertThat(repository.findByEmailForUpdate("nobody@example.com")).isEmpty();
    }

    private static UserAccount account(String email, String displayName) {
        return new UserAccount(email, displayName, "$2a$12$notARealHashButLongEnoughForTheColumn", NOW);
    }
}
