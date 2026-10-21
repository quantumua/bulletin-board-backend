package com.example.bulletinboard.auth;

/**
 * The caller of a request: a logged-in account, or anonymous. Controllers resolve it from the
 * security context and pass it explicitly to the business logic, which never reads identity from a
 * request body (ADR-005, NFR5.2).
 *
 * @param accountId the caller's account id, or {@code null} for an anonymous visitor
 */
public record CurrentUser(Long accountId) {

    private static final CurrentUser ANONYMOUS = new CurrentUser(null);

    public static CurrentUser anonymous() {
        return ANONYMOUS;
    }

    public static CurrentUser of(long accountId) {
        return new CurrentUser(accountId);
    }

    public boolean isAuthenticated() {
        return accountId != null;
    }

    /**
     * Whether this caller is the given owner. Always false for an anonymous caller or an unowned ad.
     */
    public boolean owns(Long ownerId) {
        return accountId != null && accountId.equals(ownerId);
    }

    /**
     * The account id, or fails with 401 when the caller is anonymous.
     */
    public long requireAccountId() {
        if (accountId == null) {
            throw new NotAuthenticatedException();
        }
        return accountId;
    }
}
