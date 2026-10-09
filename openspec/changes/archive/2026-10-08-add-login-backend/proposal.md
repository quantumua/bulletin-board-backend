# Proposal

## Why

The backend currently lets anyone create and edit any ad. The `add-login` change in the `team-plans` store defines the contract for accounts, cookie-session login and ad ownership; this change plans only the backend's implementation of it.

## What Changes

- Add `/api/auth` endpoints: `POST /register`, `POST /login`, `GET /me`, `POST /logout` (per `user-auth`).
- Add Spring Security with a server-side session cookie (`HttpOnly`, `SameSite=Lax`) and cookie-based CSRF (`XSRF-TOKEN` / `X-XSRF-TOKEN`), exempting register and login.
- Add a `users` table with unique case-insensitive usernames and hashed passwords.
- Require login for `POST /api/ads` and `PUT /api/ads/{id}`; `GET` stays public (per `ads-api`).
- Add a nullable owner to `Ad`, expose `author` (username) in `AdResponse`, set it on create (ignoring body input) and allow only the owner to edit; legacy/ownerless ads are not editable.
- Return RFC 9457 problem details for 401/403 from the security layer.
- Replace the MVC `CorsConfig` with credentialed CORS and the `X-XSRF-TOKEN` header, using an explicit origin.
- **BREAKING**: anonymous/non-owner `POST`/`PUT` on `/api/ads` now get `401`/`403`; ad JSON gains `author`; existing tests need an authenticated session and CSRF token.

## Capabilities

### New Capabilities
None locally. `user-auth` is owned by the `team-plans` store (change `add-login`, delta `specs/user-auth`).

### Modified Capabilities
None locally. `ads-api` is owned by the `team-plans` store (existing spec plus change `add-login`, delta `specs/ads-api`). The contract is cited, not copied; this change sets `skip_specs: true` because no spec is authored in this repo.

## Impact

- **Code** (`com.example.bulletinboard`): new `auth`/`user` package, new security config, changes to `ad/Ad`, `AdResponse`, `AdService`, `AdController`, `config/CorsConfig`, and `AdControllerTests`.
- **Dependencies**: `spring-boot-starter-security` and its test starter in `pom.xml`.
- **Config**: `application.properties` (CORS origins reused; session/cookie settings).
- **Out of scope**: frontend work (planned in `bulletin-board-frontend`), contract edits, store tasks 1.x and 5.x.
