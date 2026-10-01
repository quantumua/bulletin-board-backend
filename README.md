# Bulletin Board – Backend

REST API for a bulletin board: create, list, view and edit ads.

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

## Configuration

`app.cors.allowed-origins` (in `application.properties`) lists the frontend origins allowed to call the API.
Defaults to `http://localhost:5173`.
