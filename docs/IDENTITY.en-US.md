# Auth Service — Identity and Authentication

Documentation of the Auth Service **identity endpoints**: authentication against
AD/Postgres, short-lived token issuance and domain resolution.

## 1. Overview

The Auth Service validates credentials against:

- **Active Directory** (LDAP/LDAPS) — provider `AD`
- **Local PostgreSQL** — provider `POSTGRES` (a role with SUPERUSER receives
  `POSTGRES_SUPERUSER`)
- **Another AD by domain name** — via `authenticate-domain` (resolves the domain
  and connects to the corresponding AD)

Successful authentication may write the `sso_token` cookie (when called via
`/api/v1/identity/authenticate`), enabling SSO with Grafana/ERP on the same host.

## 2. Identity endpoints (`/api/v1/identity`)

| Method | Route | Auth | Description |
|--------|-------|------|-------------|
| POST | `/authenticate` | — | Authenticates against AD/Postgres and writes the SSO cookie |
| POST | `/token` | — | Authenticates and issues a short-lived Bearer token (JSON) |
| GET | `/me` | Bearer/session | Returns the authenticated identity |
| GET | `/groups` | Bearer/session | Identity + groups |
| GET | `/provider` | Bearer/session | Identity + provider used |
| GET | `/authenticated` | Bearer/session | Authenticated identity (implicit boolean) |
| POST | `/authenticate-domain` | — | Authenticates against another AD by domain name |
| GET | `/resolve-domain?domain=` | — | Shows where the domain resolves (DNS/LDAPS) |

## 3. Request contract

### `/authenticate` and `/token`

```json
{ "username": "john", "password": "••••", "provider": "AD" }
```

- `provider`: `AD` (default) or `POSTGRES`.
- `/authenticate` response: `IdentityDto` (username, provider, groups) +
  `sso_token` cookie.
- `/token` response: `{ "accessToken": "...", "tokenType": "Bearer",
  "expiresIn": 300 }`.

### `/authenticate-domain`

```json
{ "username": "john", "password": "••••", "domain": "srvcloud.cloud" }
```

Resolves the domain via DNS and authenticates against the corresponding AD.

### `/resolve-domain?domain=srvcloud.cloud`

Returns `{ domain, ips, ldapUrl, baseDn }` or 404 if not resolved.

## 4. Identity and groups

- `IdentityDto`: `{ username, provider, groups[] }`.
- In AD, groups come from `memberOf` (and the primary group `Domain Users`
  when `primaryGroupID=513`).
- In Postgres, the `POSTGRES_SUPERUSER` group is added only if the role has
  `rolsuper` (see `POSTGRES_SUPERUSER.md`).
- The identity can be resolved from a Bearer JWT (own issuer) or from the HTTP
  session.

## 5. Served pages

| Route | Description |
|-------|-------------|
| `/admin`, `/admin/` | Administrator panel (issue/revoke/MFA) |
| `/mfa`, `/mfa/` | User flow (MFA + certificate download) |
| `/oauth2/login` | OAuth2 issuer login form |

## 6. Errors

| Status | Cause |
|--------|-------|
| 401 | Invalid credentials (`BadCredentialsException`) |
| 503 | Provider unavailable (`ProviderUnavailableException`) |
| 404 | Domain not resolved (`resolve-domain`) |

## 7. Security

- Passwords are never logged; only the result (success/failure) is audited.
- The `sso_token` cookie is HttpOnly, SameSite=Lax, valid for 8 hours.
- `/me`, `/groups`, `/provider`, `/authenticated` require authentication
  (Bearer or session).
- `/authenticate`, `/token`, `/authenticate-domain`, `/resolve-domain` are public
  (used by login).