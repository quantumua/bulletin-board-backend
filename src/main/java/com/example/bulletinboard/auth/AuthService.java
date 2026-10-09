package com.example.bulletinboard.auth;

import com.example.bulletinboard.user.AppUser;
import com.example.bulletinboard.user.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository repository;
    private final PasswordEncoder passwordEncoder;

    public AuthService(UserRepository repository, PasswordEncoder passwordEncoder) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
    }

    public UserResponse register(RegisterRequest request) {
        if (repository.existsByUsernameLower(AppUser.normalize(request.username()))) {
            throw new UsernameTakenException(request.username());
        }
        try {
            AppUser user = new AppUser(request.username(), passwordEncoder.encode(request.password()));
            return UserResponse.from(repository.saveAndFlush(user));
        } catch (DataIntegrityViolationException e) {
            // Lost a race with a concurrent registration of the same username
            throw new UsernameTakenException(request.username());
        }
    }

    @Transactional(readOnly = true)
    public UserResponse findByUsername(String username) {
        return repository.findByUsernameLower(AppUser.normalize(username)).map(UserResponse::from)
                .orElseThrow(InvalidCredentialsException::new);
    }
}
