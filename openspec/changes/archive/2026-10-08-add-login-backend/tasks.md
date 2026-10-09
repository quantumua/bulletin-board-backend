# Tasks

Backend-only work for store change `add-login` (`team-plans`). Requirements: `user-auth` and `ads-api` in that change (`openspec show add-login --store team-plans`). Frontend and contract tasks live elsewhere.

## 1. Security foundation

- [x] 1.1 Add `spring-boot-starter-security` and `spring-boot-starter-security-test` (or `spring-security-test`) to `pom.xml`, and verify `./mvnw test` compiles
- [x] 1.2 Add a `SecurityConfig` filter chain (permit `GET /api/ads/**` and register/login/logout, authenticate `me` and ad writes), a `CorsConfigurationSource` from `app.cors.allowed-origins` with credentials and `Content-Type`/`X-XSRF-TOKEN` headers, and delete `CorsConfig`; verify preflight tests (allowed origin with credentials, unknown origin rejected) per `ads-api` Cross-origin access
- [x] 1.3 Enable cookie CSRF (`XSRF-TOKEN` readable cookie, plain token handler, register/login exempt) with a filter that always emits the cookie; verify tests for missing token (403) and valid token per `user-auth` CSRF protection
- [x] 1.4 Add 401 `AuthenticationEntryPoint` and 403 `AccessDeniedHandler` writing RFC 9457 `application/problem+json`; verify response body assertions in tests
- [x] 1.5 Set session cookie `HttpOnly` and `SameSite=Lax` in `application.properties`; verify the login test asserts the `Set-Cookie` attributes

## 2. Accounts and sessions (`user-auth`)

- [x] 2.1 Add `AppUser`, `UserRepository`, `UserDetailsService` and BCrypt `PasswordEncoder`; verify a repository test for case-insensitive uniqueness
- [x] 2.2 Implement `POST /api/auth/register` with validation (username 3-50, password 8-72), 409 on duplicate ignoring case, no password in responses; verify `AuthControllerTests` for 201, 409 and 400 scenarios and that no session is created
- [x] 2.3 Implement `POST /api/auth/login` with a generic 401 for unknown user and wrong password and session id rotation; verify tests for valid credentials (200, body, cookie) and both invalid cases (no cookie, identical body)
- [x] 2.4 Implement `GET /api/auth/me` and `POST /api/auth/logout` (204 for logged-in and anonymous, cookie expired, session invalidated); verify tests for logged-in, anonymous 401, and reuse of the old cookie being anonymous

## 3. Ad protection and ownership (`ads-api`)

- [x] 3.1 Add nullable `owner` to `Ad`, `author` to `AdResponse` (null for ownerless), and an entity graph/fetch join in `AdRepository`; verify existing ad tests updated and passing, including `author: null` for an ownerless ad
- [x] 3.2 Require login for `POST /api/ads`, set the owner from the principal and ignore body `author`; verify tests for anonymous create 401, anonymous read 200, and created ad `author` equals the logged-in username
- [x] 3.3 Enforce owner-only `PUT /api/ads/{id}` with `AdForbiddenException` mapped to 403 problem detail (non-owner and ownerless), 404 for unknown id, `author` and `createdAt` unchanged; verify each `Ad ownership` and `Edit an ad` scenario in tests
- [x] 3.4 Update the README with the auth endpoints, CSRF header usage and `app.cors.allowed-origins`, and verify a documented curl login, create and logout sequence works as written against `./mvnw spring-boot:run`

## 4. Wrap-up

- [x] 4.1 Run `./mvnw verify` and verify all tests pass, then run `openspec validate add-login-backend --strict` and verify it passes
