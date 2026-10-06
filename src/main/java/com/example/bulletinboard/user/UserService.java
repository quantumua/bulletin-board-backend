package com.example.bulletinboard.user;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository repository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository repository, PasswordEncoder passwordEncoder) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
    }

    static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    @Transactional
    public UserResponse register(RegisterRequest request) {
        String email = normalizeEmail(request.email());
        if (repository.findByEmail(email).isPresent()) {
            throw new EmailAlreadyRegisteredException(null);
        }
        try {
            return UserResponse.from(repository.saveAndFlush(new User(email, passwordEncoder.encode(request.password()))));
        } catch (DataIntegrityViolationException e) {
            // Another request registered the same email between the check and the insert
            throw new EmailAlreadyRegisteredException(e);
        }
    }

    public Optional<UserResponse> findByEmail(String email) {
        return repository.findByEmail(normalizeEmail(email)).map(UserResponse::from);
    }
}
