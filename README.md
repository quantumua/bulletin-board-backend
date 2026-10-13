# Bulletin Board – Backend

REST API for a bulletin board: register, log in, and create, list, view and edit ads.

**Stack:** Java 25, Spring Boot 4.1, Maven, Spring Data JPA, H2 (in-memory).

## Run

Requires JDK 25 or newer (`JAVA_HOME` must point to it). The Maven wrapper is included.

```bash
./mvnw spring-boot:run
```

The API starts on http://localhost:8080. Data lives in memory and is lost on restart.
The H2 console is enabled in Spring Boot but is not reachable while Spring Security protects every non-API path (it answers `401`).

## Test

```bash
./mvnw test
```

## API

| Method | Path                 | Auth     | Description            |
|--------|----------------------|----------|------------------------|
| GET    | `/api/ads`           | public   | List ads, newest first |
| GET    | `/api/ads/{id}`      | public   | Get one ad             |
| POST   | `/api/ads`           | login    | Create an ad (you become its owner) |
| PUT    | `/api/ads/{id}`      | owner    | Update an ad           |
| POST   | `/api/auth/register` | public   | Create an account      |
| POST   | `/api/auth/login`    | public   | Log in (sets session cookie) |
| GET    | `/api/auth/me`       | login    | Current user           |
| POST   | `/api/auth/logout`   | public   | Log out                |
| GET    | `/api/users`         | login    | List registered users, newest registration first |

Ad request body for POST/PUT:

```json
{ "title": "Bike for sale", "description": "Almost new" }
```

`title` is required (max 100 chars), `description` is required (max 2000 chars).
Ads in responses carry `author`, the owner's username (`null` for ads that predate login; those cannot be edited).
Errors are returned as RFC 9457 problem details (`401` not logged in, `403` not the owner or missing CSRF token,
`409` username taken).

`GET /api/users` returns `[{ "id": 1, "username": "alice", "createdAt": "2026-10-09T10:15:30.123456Z" }, ...]`:
exactly these three fields, no password data, and it sets no cookies (not even `XSRF-TOKEN`). `POST`, `PUT` and
`DELETE` on it are not offered (`405` with a valid CSRF token, `403` without one, `401` when anonymous).
Anonymous `401` responses never start a session.

Account body for register and login: `{ "username": "alice", "password": "s3cret-pass" }`
(username 3-50 characters, unique ignoring case; password 8-72 characters).

### Login session and CSRF

Login sets an `HttpOnly`, `SameSite=Lax` `JSESSIONID` cookie. Every response also carries a readable `XSRF-TOKEN` cookie.
Once you have a session, `POST` and `PUT` requests (including logout) must echo that cookie's value in an
`X-XSRF-TOKEN` header, otherwise they get `403`. Register and login need no token. Browsers must send
requests with credentials (`fetch(..., { credentials: "include" })`).

```bash
curl -c jar -H 'Content-Type: application/json'   -d '{"username":"alice","password":"s3cret-pass"}' localhost:8080/api/auth/register
curl -c jar -b jar -H 'Content-Type: application/json'   -d '{"username":"alice","password":"s3cret-pass"}' localhost:8080/api/auth/login
curl -c jar -b jar localhost:8080/api/auth/me            # issues the XSRF-TOKEN cookie
TOKEN=$(awk '$6=="XSRF-TOKEN"{print $7}' jar)
curl -b jar -H "X-XSRF-TOKEN: $TOKEN" -H 'Content-Type: application/json'   -d '{"title":"Bike","description":"Almost new"}' localhost:8080/api/ads
curl -b jar localhost:8080/api/users                       # list registered users
curl -b jar -H "X-XSRF-TOKEN: $TOKEN" -X POST localhost:8080/api/auth/logout
```

## Configuration

`app.cors.allowed-origins` (in `application.properties`) lists the frontend origins allowed to call the API,
with credentials. Each origin is echoed explicitly (never `*`). Defaults to `http://localhost:5173`.
