package com.example.bulletinboard.user;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lets Spring Security authenticate against {@link User} accounts, using the normalized email as the username.
 */
@Service
@Transactional(readOnly = true)
public class UserAccountDetailsService implements UserDetailsService {

    private final UserRepository repository;

    public UserAccountDetailsService(UserRepository repository) {
        this.repository = repository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) {
        User user = repository.findByEmail(UserService.normalizeEmail(username))
                .orElseThrow(() -> new UsernameNotFoundException("No account for " + username));
        return org.springframework.security.core.userdetails.User.withUsername(user.getEmail())
                .password(user.getPasswordHash())
                .build();
    }
}
