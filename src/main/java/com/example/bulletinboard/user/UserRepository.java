package com.example.bulletinboard.user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<AppUser, Long> {

    Optional<AppUser> findByUsernameLower(String usernameLower);

    boolean existsByUsernameLower(String usernameLower);

    // id breaks ties between users registered in the same instant, in registration order
    List<AppUser> findAllByOrderByCreatedAtDescIdDesc();
}
