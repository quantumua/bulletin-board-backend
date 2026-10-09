# Design

## Context

Observed: the backend is Spring Boot 4.1.1 with `webmvc`, JPA and H2 (`ddl-auto=create-drop`), no security dependency. `AdController`/`AdService` are open; `Ad` has no owner; `AdResponse` has no `author`. CORS is an MVC `WebMvcConfigurer` (`CorsConfig`) reading `app.cors.allowed-origins`, allowing `GET/POST/PUT` without credentials. Problem details are enabled via `spring.mvc.problemdetails.enabled=true`. Tests are `@SpringBootTest` + `MockMvc`.

Requirements come from the store specs and are not restated here: `user-auth` and `ads-api` (change `add-login` in `team-plans`; read with `openspec show add-login --store team-plans`). See proposal.md for motivation. The design decisions in the store's `design.md` (session cookie, double-submit CSRF, owner by username, ownerless legacy ads, status codes) are taken as given.

## Goals / Non-Goals

**Goals:**
- Implement every scenario of `user-auth` and the changed `ads-api` requirements in this repo, each covered by a test.
- Keep security wiring in one place so CORS, CSRF and 401/403 behave consistently.

**Non-Goals:**
- Frontend changes, contract changes, password reset, roles, rate limiting, delete endpoint.
- Persistent sessions or a shared session store.

## Decisions

**Spring Security filter chain owns CORS, CSRF and auth.** Add `spring-boot-starter-security` (plus its test starter). Once Security is present, MVC-level CORS is bypassed for preflights rejected by the filter chain, so move CORS into a `CorsConfigurationSource` bean used by `http.cors(...)` and delete `CorsConfig`. Keep `app.cors.allowed-origins`; set `allowCredentials(true)`, methods `GET/POST/PUT/OPTIONS`, headers `Content-Type`, `X-XSRF-TOKEN`, never `*`. Alternative: keep `CorsConfig`; rejected as two sources of truth.

**Cookie CSRF.** `CookieCsrfTokenRepository.withHttpOnlyFalse()` with the default request handler that resolves the raw token (not the BREACH-masked `XorCsrfTokenRequestAttributeHandler`), so the SPA can echo the cookie value. Ignore CSRF for `POST /api/auth/register` and `/api/auth/login`. The token cookie must be materialized on responses that do not render it (e.g. via a small filter that touches the deferred token), otherwise the SPA never receives it on first load.

**Authentication via JSON login endpoint, not form login.** `AuthController` handles `/api/auth/login` using `AuthenticationManager` with `DaoAuthenticationProvider`, then saves the `SecurityContext` to the `HttpSessionSecurityContextRepository`, and rotates the session id (session fixation protection). Session cookie is configured with `server.servlet.session.cookie.http-only=true` and `same-site=lax`. Logout invalidates the session, expires `JSESSIONID`, and returns `204` for anonymous callers too. Alternative: `formLogin`/`JsonUsernamePasswordAuthenticationFilter`; rejected, a controller is simpler to test against the spec's response bodies.

**Users.** New `user` package: `AppUser` entity (`users` table; `id`, `username`, `usernameLower` unique, `passwordHash`), `UserRepository`, `UserDetailsService` backed by it. Case-insensitive uniqueness is enforced by a unique column on the lower-cased name, with the service pre-check mapped to `409` and a `DataIntegrityViolationException` fallback for races. Passwords hashed with `PasswordEncoderFactories`-style `BCryptPasswordEncoder`; request validation `username` 3-50, `password` 8-72 via Bean Validation (`400` problem detail). Login failure uses one generic message for unknown user and bad password.

**Ownership.** `Ad` gets a nullable `@ManyToOne AppUser owner` (legacy/null rows stay ownerless; the DB is in-memory, so no migration script). `AdResponse.author` is `owner.username` or `null`; `AdRequest` ignores any `author` property. `AdService.create/update` take the authenticated username: update throws a new `AdForbiddenException` when the owner is null or differs, after the 404 check, mapped to `403`. Alternative: `@PreAuthorize` on the method; rejected since the ownership check needs the loaded entity anyway. Lists must avoid N+1 on `owner` (fetch join or `@EntityGraph` in `AdRepository`) because `open-in-view=false`.

**Authorization rules.** `GET /api/ads/**` and `/api/auth/register|login|logout` permitted; `/api/auth/me` and `POST`/`PUT /api/ads/**` authenticated. `/h2-console` is out of scope for these rules and stays reachable only as today in dev; note it needs frame/CSRF exemptions if kept enabled.

**Problem details from the security layer.** A custom `AuthenticationEntryPoint` (401) and `AccessDeniedHandler` (403, covers missing CSRF) write `ProblemDetail` JSON with `application/problem+json`, matching MVC-produced errors. Controller-level exceptions (`409`, `403` ownership) go through the existing `@RestControllerAdvice` style/`ErrorResponse` exceptions.

**Testing.** Existing `AdControllerTests` adopt `spring-security-test` (`user(...)`, `csrf()`), and new `AuthControllerTests` cover register/login/me/logout and CSRF with real cookies.

## Risks / Trade-offs

- [Security on the classpath changes every existing test] → Update `AdControllerTests` first and keep it green before adding features.
- [Masked CSRF token handler would break the SPA's double-submit] → Use the plain handler and test the header/cookie round-trip.
- [CORS preflight rejected by Security] → Register the CORS source in the filter chain and test preflight with credentials and unknown origin.
- [In-memory sessions lost on restart] → Accepted, per store design.
- [Username enumeration through `409`] → Accepted, per store design.
- [Existing ads uneditable] → Documented behavior in the contract.
