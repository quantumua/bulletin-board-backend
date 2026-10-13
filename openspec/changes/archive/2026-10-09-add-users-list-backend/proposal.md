# Proposal

## Why

The frontend's new "Users" tab needs a list of registered users. The store change `add-users-list` (`team-plans`) defines the `user-directory` contract; this change plans only the backend's implementation of it.

## What Changes

- Add `GET /api/users` returning every registered user as `id`, `username` and `createdAt`, newest registration first, for logged-in clients only (per `user-directory`).
- Record a registration time (`createdAt`) on accounts when they register; the register response is unchanged.
- Return the contract's response shape from a dedicated type so no entity field (password hash, lower-cased key) can leak.
- Make `/api/users` set no cookies: the `XSRF-TOKEN` cookie filter must skip this path (see design.md for why this is a decision and not a given).
- No change to ads, login, register, logout, CORS or the CSRF rules.

## Capabilities

### New Capabilities
None locally. `user-directory` is owned by the `team-plans` store (change `add-users-list`, delta `specs/user-directory`).

### Modified Capabilities
None locally. `user-auth` is only touched indirectly (accounts gain `createdAt`, response unchanged). The contract is cited, not copied; this change sets `skip_specs: true` because no spec is authored in this repo.

## Impact

- **Code** (`com.example.bulletinboard`): `user/AppUser` (new column), `user/UserRepository` (ordered query), new `user` controller, service and response type, `config/CsrfCookieFilter` (skip `/api/users`), tests, README.
- **Database:** new non-null `created_at` column on `users`. H2 is in-memory and recreated on start, so there is no migration.
- **API:** one new read endpoint; behavior of existing endpoints is unchanged.
- **Dependencies:** none.
- **Out of scope:** the frontend work (`add-users-list-frontend`), contract edits, pagination, search, profiles, store tasks 1.x and 3.x.
