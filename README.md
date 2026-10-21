# Bulletin Board – Backend

REST API for a bulletin board: sign up, log in and out, and create, list, view and edit ads.
Anyone can read ads; only logged-in users can post, and only the poster can edit their own ad.

**Stack:** Java 25, Spring Boot 4.1, Maven, Spring Data JPA, Spring Security, H2 (in-memory).

## Run

Requires JDK 25 or newer (`JAVA_HOME` must point to it). The Maven wrapper is included.

```bash
./mvnw spring-boot:run
```

The API starts on http://localhost:8080. Data lives in memory and is lost on restart: accounts,
sessions, lockouts and ads all start empty again.

## Test

```bash
./mvnw test
```

## API

The current API is `/api/v1`. The unversioned `/api/ads` path is kept for old clients and is deprecated.

### Authentication – `/api/v1/auth`

| Method | Path                    | Description                                                    |
|--------|-------------------------|----------------------------------------------------------------|
| GET    | `/api/v1/auth/me`       | Current user `{ "user": { id, email, displayName } }`, or `{ "user": null }` for a visitor. Also issues the `XSRF-TOKEN` cookie. |
| POST   | `/api/v1/auth/signup`   | Create an account and log in. `201` with the user.             |
| POST   | `/api/v1/auth/login`    | Log in. `200` with the user.                                   |
| POST   | `/api/v1/auth/logout`   | Log out. `204`, also when nobody is logged in.                 |

Sign-up body:

```json
{ "email": "ann@example.com", "displayName": "Ann", "password": "correct-horse" }
```

- `email`: a valid address of at most 254 characters; surrounding spaces are removed and case is ignored. One account per email.
- `displayName`: 1–50 visible characters after trimming. Shown on the user's ads; need not be unique.
- `password`: 8–128 characters and at most 72 bytes in UTF-8 (a BCrypt limit: fewer characters for non-Latin text).

Log-in body: `{ "email": "...", "password": "..." }`. After 5 wrong passwords in a row the account is
locked for 15 minutes (`423`), even for the right password. A successful log-in resets the count.

### Ads – `/api/v1/ads`

| Method | Path                 | Who                | Description                     |
|--------|----------------------|--------------------|---------------------------------|
| GET    | `/api/v1/ads`        | anyone             | List ads, newest first          |
| GET    | `/api/v1/ads/{id}`   | anyone             | Get one ad                      |
| POST   | `/api/v1/ads`        | logged-in user     | Create an ad owned by the caller (`201`, `Location: /api/v1/ads/{id}`) |
| PUT    | `/api/v1/ads/{id}`   | the ad's owner     | Update title and description    |

Request body for POST/PUT:

```json
{ "title": "Bike for sale", "description": "Almost new" }
```

`title` is required (max 100 chars), `description` is required (max 2000 chars). Unknown fields are
ignored; in particular the owner is always the logged-in caller and can never be set or changed
through the body.

Response:

```json
{
  "id": 1, "title": "Bike for sale", "description": "Almost new",
  "createdAt": "2026-10-20T10:00:00Z", "updatedAt": "2026-10-20T10:00:00Z",
  "authorName": "Ann", "ownedByMe": true
}
```

`authorName` is the poster's display name, or `Unknown` for an ad without an owner. Ads without an
owner cannot be edited by anyone. No ad response ever contains an email address.

### Legacy ads – `/api/ads` (deprecated)

`GET /api/ads`, `GET /api/ads/{id}`, `POST /api/ads` and `PUT /api/ads/{id}` keep the old response
shape `{ id, title, description, createdAt, updatedAt }` but follow exactly the same login,
ownership and anti-forgery rules as `/api/v1/ads`. Every response carries
`Deprecation: true` and `Link: </api/v1/ads>; rel="successor-version"`. New clients must use `/api/v1`.

## Sessions and anti-forgery token

- **Session cookie** `JSESSIONID`: set at sign-up and log-in (with a new id every time), `HttpOnly`,
  `SameSite=Lax`, `Path=/`, `Secure` over HTTPS, and without an expiry date, so it ends when the
  browser closes or the user logs out. There is no server-side idle timeout. The frontend never reads it.
- **Anti-forgery token**: the backend sets the script-readable cookie `XSRF-TOKEN`. Every `POST` and
  `PUT` — including sign-up, log-in and log-out — must send the same value in the `X-XSRF-TOKEN` header.
  The token is replaced after sign-up, log-in and log-out, so re-read the cookie before each request.
- The API is designed to be served from the same origin as the frontend (the Vite dev proxy in development).

## Errors

Errors are RFC 9457 problem details (`application/problem+json`) with `status`, `title`, `detail` and a
stable `code`. Clients should act on `code`, not on the `detail` text. Validation errors also carry
`errors: [{ "field", "message" }]`.

| Status | `code`                   | When                                                      |
|--------|--------------------------|-----------------------------------------------------------|
| 400    | `validation_failed`      | A field is invalid (see `errors`)                         |
| 400    | `bad_request`            | Malformed JSON or a non-numeric id                        |
| 401    | `not_authenticated`      | Not logged in                                             |
| 401    | `bad_credentials`        | Wrong email or password at log-in                         |
| 403    | `csrf_invalid`           | Missing or wrong `X-XSRF-TOKEN`                           |
| 403    | `not_owner`              | Editing someone else's ad                                 |
| 403    | `unowned`                | Editing an ad without an owner                            |
| 403    | `forbidden`              | Any other refused request                                 |
| 404    | `ad_not_found`           | The ad does not exist                                     |
| 404    | `not_found`              | Unknown path                                              |
| 405    | `method_not_allowed`     | HTTP method not supported on the path                     |
| 409    | `email_taken`            | Email already registered                                  |
| 415    | `unsupported_media_type` | Body is not JSON                                          |
| 423    | `account_locked`         | Account temporarily locked after 5 failed log-ins         |
| 500    | `internal_error`         | Unexpected server error (details are only in the server log) |

Protected writes are checked in this order: anti-forgery token (403), login (401), body (400),
ad exists (404), owner (403).

## Security log

Security events are written at INFO to the `security` logger, one line each:
`event=<signup|login_success|login_failure|account_locked|logout|write_refused> accountId=<id|none> outcome=<...> path=<path>`.
They never contain an email, password, session id or token.

## H2 console (trusted environments only)

The H2 console at http://localhost:8080/h2-console (JDBC URL `jdbc:h2:mem:bulletinboard`, user `sa`,
empty password) stays open, without login or anti-forgery checks. It can read every account and change
any ad's owner, so it bypasses all of the rules above. Run this version only in trusted environments,
and close the console before any wider deployment.

Log-in and sign-up have no rate limit in this version (accepted for trusted environments); the
per-account lockout is the only brake on password guessing.

## Configuration

`app.cors.allowed-origins` (in `application.properties`) lists the frontend origins allowed to call the API.
Defaults to `http://localhost:5173`.
