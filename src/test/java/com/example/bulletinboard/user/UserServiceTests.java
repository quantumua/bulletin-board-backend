package com.example.bulletinboard.user;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class UserServiceTests {

    @Autowired
    private UserService service;

    @Autowired
    private UserDetailsService userDetailsService;

    @Autowired
    private UserRepository repository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void cleanDatabase() {
        repository.deleteAll();
    }

    @Test
    void storesNormalizedEmailAndHashedPassword() {
        UserResponse registered = service.register(new RegisterRequest(" Alice@Example.COM ", "correct-horse"));

        assertThat(registered.email()).isEqualTo("alice@example.com");
        User stored = repository.findById(registered.id()).orElseThrow();
        assertThat(stored.getEmail()).isEqualTo("alice@example.com");
        assertThat(stored.getPasswordHash()).isNotEqualTo("correct-horse");
        assertThat(passwordEncoder.matches("correct-horse", stored.getPasswordHash())).isTrue();
    }

    @Test
    void rejectsDuplicateEmailIgnoringCaseAndWhitespace() {
        service.register(new RegisterRequest("alice@example.com", "correct-horse"));

        assertThatThrownBy(() -> service.register(new RegisterRequest(" Alice@Example.COM ", "another-pass")))
                .isInstanceOf(EmailAlreadyRegisteredException.class);
        assertThat(repository.count()).isEqualTo(1);
    }

    @Test
    void loadsUserDetailsByNormalizedEmail() {
        service.register(new RegisterRequest("alice@example.com", "correct-horse"));

        UserDetails details = userDetailsService.loadUserByUsername("ALICE@example.com ");

        assertThat(details.getUsername()).isEqualTo("alice@example.com");
        assertThat(passwordEncoder.matches("correct-horse", details.getPassword())).isTrue();
    }

    @Test
    void failsToLoadUnknownUser() {
        assertThatThrownBy(() -> userDetailsService.loadUserByUsername("nobody@example.com"))
                .isInstanceOf(UsernameNotFoundException.class);
    }
}
