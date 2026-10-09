package com.example.bulletinboard.user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<AppUser, Long> {

    Optional<AppUser> findByUsernameLower(String usernameLower);

    boolean existsByUsernameLower(String usernameLower);
}
