package com.example.bulletinboard.account;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/**
 * Accounts are looked up by their normalised email (trimmed, lower-case). Batched display-name
 * lookups use the inherited {@code findAllById}.
 */
public interface UserAccountRepository extends JpaRepository<UserAccount, Long> {

    Optional<UserAccount> findByEmail(String normalisedEmail);

    /**
     * Reads the account under a pessimistic write lock so concurrent log-in attempts on the same
     * account update the failure count one after another, without lost updates (NFR3.6).
     * Must be called inside a transaction.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from UserAccount a where a.email = :email")
    Optional<UserAccount> findByEmailForUpdate(@Param("email") String normalisedEmail);
}
