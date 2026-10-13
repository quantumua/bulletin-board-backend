# Design

## Context

Observed: `AppUser` (`users` table) has `id`, `username`, `usernameLower` (unique) and `passwordHash`, and no timestamp. `Ad` already shows the pattern for timestamps (`Instant`, set in `@PrePersist`, serialized as ISO-8601 UTC). `SecurityConfig` permits only `GET /api/ads`, `GET /api/ads/*` and the register/login/logout posts; `anyRequest().authenticated()` covers everything else, and the security layer returns RFC 9457 problem details for 401/403. `CsrfCookieFilter` reads the CSRF token on every response so the readable `XSRF-TOKEN` cookie is issued. `/h2-console` currently answers `401`.

Requirements come from the store spec `user-directory` (change `add-users-list`, read with `openspec show add-users-list --store team-plans`); they are not restated here. See proposal.md for motivation.

## Goals / Non-Goals

**Goals:**
- Implement every `user-directory` scenario in this repo, each covered by a test.
- Keep the response type independent of the entity so fields cannot leak by accident.

**Non-Goals:**
- Pagination, search, profiles, roles, deleting users, changing register/login/me responses, frontend work.

## Decisions

**`createdAt` on `AppUser`, set in `@PrePersist`, like `Ad`.** `Instant`, `nullable = false`, `updatable = false`, plus `@ColumnDefault("CURRENT_TIMESTAMP")` so the schema has a default as the store design asks. The default is only a safety net: `@PrePersist` always sets the value, so registration needs no change to `AuthService` and the register response stays as is. Alternative: setting the time in the service; rejected so every code path that creates a user (including tests that save entities directly) gets a timestamp.

**Newest first with an `id` tie-breaker.** The repository gets `findAllByOrderByCreatedAtDescIdDesc`. Two users registered within the same clock tick would otherwise have an undefined order; `id` is monotonic, so it breaks ties the same way registration order does. This still satisfies "ordered newest first by `createdAt`".

**Dedicated response record in the `user` package.** `UserDirectoryEntry(id, username, createdAt)`, built from `AppUser`. It is not the existing `auth.UserResponse` (no `createdAt`, different contract) and not the entity. A test asserts the JSON has exactly three fields.

**No security rule needed for access control; add tests instead.** `GET /api/users` already falls under `anyRequest().authenticated()`, so anonymous callers get the existing `401` problem detail. Adding an explicit matcher would only repeat that; tests pin the behavior so a future permissive rule fails loudly.

**Write methods: the controller maps only `GET`.** A logged-in `POST`/`PUT`/`DELETE` that passes CSRF reaches MVC and gets `405` (problem detail, via `spring.mvc.problemdetails.enabled`); anonymous ones get `401`. See Clarifications for the CSRF interaction.

**`/api/users` issues no `XSRF-TOKEN` cookie.** The spec says the endpoint sets no cookies, but `CsrfCookieFilter` currently makes every response, including `GET`s, carry the cookie when the client has none. The filter skips `/api/users` (and therefore also its `401`). The frontend is unaffected: it gets the token from `/api/auth/me`, `/api/ads` and login responses, which are unchanged. Alternative: read the spec as "no session cookies" and change nothing; rejected because it contradicts the literal requirement and a client with no token cookie would see a `Set-Cookie`.

**Disable the Spring Security request cache globally.** Implementation found that the anonymous `401` on `/api/users` still set `JSESSIONID`: the request cache saves the rejected request into a new session when the entry point fires. This happened on every anonymous `401` (including `GET /api/auth/me`, which the frontend calls on page load), and because CSRF protection applies whenever a session exists, a following anonymous `POST /api/ads` returned `403` instead of the specified `401`. A REST API has no page to redirect back to, so `SecurityConfig` calls `requestCache(cache -> cache.disable())`. Alternative: scope the exclusion to `/api/users` with a request matcher; rejected because it would leave the same bug on every other endpoint.

**Cookie checks run against a real server.** MockMvc did not reliably expose the CSRF cookie in the login work, so a MockMvc "no `Set-Cookie`" test could pass without proving anything. The cookie assertions go in the existing real-container test style (`SessionCookieTests`), with a positive control: `GET /api/ads` without a cookie does set `XSRF-TOKEN`, while `GET /api/users` does not.

**Paths outside the API are covered by regression tests only.** Public `GET /api/ads` and the `401` on `/h2-console` already hold; the spec requirement is satisfied by tests, not new code.

## Risks / Trade-offs

- [Disabling the request cache removes redirect-after-login behavior] → The API never redirects (no form login), so nothing relied on it; regression tests cover the no-session and 401-not-403 cases.
- [A future path-by-path exception in `CsrfCookieFilter` is easy to forget] → Keep the exclusion as one named constant with a comment pointing at `user-directory`, and the positive-control test.
- [Timestamp precision: the database may truncate below the Java `Instant`, so a test comparing `createdAt` with a captured "before" can fail by nanoseconds] → Tests capture the before-time truncated to milliseconds.
- [Any logged-in user sees every username] → Accepted in the store design; no rate limiting here.
- [No pagination: the response grows with users] → Accepted at current scale.
- [In-memory database: existing rows have no `created_at`] → The database is recreated on start, so there are none; the column default is a safety net.

## Clarifications

Contract gaps found while planning and implementing, to be recorded in the store design's Clarifications section (a task covers the pull request):

- A logged-in `POST`/`PUT`/`DELETE` to `/api/users` returns `405` only when it carries a valid CSRF token. Without the token, the CSRF check runs first and the response is `403`. Anonymous requests are `401` either way.
- Anonymous `401` responses start no session on any endpoint (request cache disabled); this also keeps an anonymous write after an anonymous read at `401` and not a CSRF `403`.
- "Sets no cookies" includes the `XSRF-TOKEN` cookie that other endpoints issue; the backend suppresses it on `/api/users`.
