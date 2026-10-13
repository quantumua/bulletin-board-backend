# Tasks

Backend-only work for store change `add-users-list` (`team-plans`). Requirements: `user-directory` in that change (`openspec show add-users-list --store team-plans`). Frontend and contract tasks live elsewhere.

## 1. Registration time

- [x] 1.1 Add `createdAt` (`Instant`, non-null, not updatable, `@ColumnDefault`, set in `@PrePersist`) and a getter to `AppUser`; verify a repository test that a saved user has a non-null `createdAt` that does not change on update
- [x] 1.2 Verify registration records the time without changing the response: extend `AuthControllerTests` so a registered user's stored `createdAt` is at or after the (millisecond-truncated) time before the request, and the register body still has only `id` and `username`

## 2. Directory endpoint

- [x] 2.1 Add `findAllByOrderByCreatedAtDescIdDesc` to `UserRepository`, a `UserDirectoryEntry(id, username, createdAt)` record, a service and a `GET /api/users` controller; verify a `UserDirectoryControllerTests` case that the JSON array entries have exactly `id`, `username` and `createdAt` (no `password`, `passwordHash` or `usernameLower`) and the username keeps its original casing
- [x] 2.2 Verify ordering and counts: two registered users are returned newest first, including users saved in the same instant (tie broken by `id`), and a sole caller gets a one-element array
- [x] 2.3 Verify access control: an anonymous `GET /api/users` returns `401` with an `application/problem+json` body and no user data, and the check happens before anything else

## 3. Cookies, methods and neighboring paths

- [x] 3.1 Make `CsrfCookieFilter` skip `/api/users` (one named constant with a comment) and disable the request cache in `SecurityConfig` so anonymous `401`s start no session; verify real-server tests that `GET /api/users` sends no `Set-Cookie` header for a logged-in client and for an anonymous `401`, with a control that `GET /api/ads` without a cookie still sets `XSRF-TOKEN`, and that an anonymous `GET /api/auth/me` sets no `JSESSIONID` and a following anonymous `POST /api/ads` is `401`
- [x] 3.2 Verify write methods are not offered: logged-in `POST`, `PUT` and `DELETE` with a valid CSRF token return `405` and change nothing, the same without a token return `403`, and anonymous requests return `401`
- [x] 3.3 Verify neighboring paths are unchanged: anonymous `GET /api/ads` still returns `200` and anonymous `GET /h2-console` still returns `401`
- [x] 3.4 Verify the end-to-end scenario: register a user, log in, call `GET /api/users`, and check the new user is listed with a `createdAt` at or after the pre-registration time

## 4. Docs and wrap-up

- [x] 4.1 Add `GET /api/users` (login required, fields, ordering, no cookies) to the README API table and notes, and verify the documented curl call works against `./mvnw spring-boot:run` as written (register, log in, then list)
- [x] 4.2 Add the three items from design.md "Clarifications" to the `add-users-list` design in `bulletin-board-plans` and verify `openspec validate add-users-list --strict --store team-plans` passes (local edit only; committing and the pull request are left to the user)
- [x] 4.3 Run `./mvnw verify` and verify all tests pass, then run `openspec validate add-users-list-backend --strict` and verify it passes
