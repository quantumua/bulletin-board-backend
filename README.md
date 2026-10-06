# Bulletin Board – Backend

REST API for a bulletin board: create, list, view and edit ads; register, sign in and sign out.

**Stack:** Java 25, Spring Boot 4.1, Maven, Spring Data JPA, H2 (in-memory).

## Run

Requires JDK 25 or newer (`JAVA_HOME` must point to it). The Maven wrapper is included.

```bash
./mvnw spring-boot:run
```

The API starts on http://localhost:8080. Data lives in memory and is lost on restart.
The H2 console is at http://localhost:8080/h2-console (JDBC URL `jdbc:h2:mem:bulletinboard`, user `sa`, empty password).

## Test

```bash
./mvnw test
```

## API

| Method | Path            | Description          |
|--------|-----------------|----------------------|
| GET    | `/api/ads`      | List ads, newest first |
| GET    | `/api/ads/{id}` | Get one ad           |
| POST   | `/api/ads`      | Create an ad         |
| PUT    | `/api/ads/{id}` | Update an ad         |

Request body for POST/PUT:

```json
{ "title": "Bike for sale", "description": "Almost new" }
```

`title` is required (max 100 chars), `description` is required (max 2000 chars).
Errors are returned as RFC 9457 problem details.

Ad endpoints do not require signing in.

### Accounts

| Method | Path                 | Body                  | Success                       | Errors   |
|--------|----------------------|-----------------------|-------------------------------|----------|
| POST   | `/api/auth/register` | `{email, password}`   | 201 `{id, email}`, signed in  | 400, 409 |
| POST   | `/api/auth/login`    | `{email, password}`   | 200 `{id, email}`             | 400, 401 |
| POST   | `/api/auth/logout`   | –                     | 204                           | –        |
| GET    | `/api/auth/me`       | –                     | 200 `{id, email}`             | 401      |

```json
{ "email": "alice@example.com", "password": "correct-horse" }
```

`email` must be a valid address (max 254 chars); it is case-insensitive and surrounding whitespace is ignored.
`password` must be at least 8 characters and at most 72 bytes in UTF-8 (bcrypt's limit; fewer than 72 characters for non-ASCII passwords). A failed sign-in always answers 401 "Invalid email or password".

Sign-in is kept in a server-side session (`JSESSIONID` cookie, HttpOnly), so clients must send cookies
(`fetch(..., { credentials: 'include' })`).

**CSRF:** `POST` requests to `/api/auth/**` must carry the token from the `XSRF-TOKEN` cookie in an
`X-XSRF-TOKEN` header. The cookie is set on any API response, e.g. `GET /api/auth/me`, and is replaced on sign-in and
registration, so always read the current cookie value. Ad endpoints are exempt.

Accounts and sessions live in the in-memory H2 database / server memory and are **lost on restart**.

## Configuration

`app.cors.allowed-origins` (in `application.properties`) lists the frontend origins allowed to call the API.
Defaults to `http://localhost:5173`. Credentials (cookies) are allowed, so origins must be listed explicitly — no wildcards.
