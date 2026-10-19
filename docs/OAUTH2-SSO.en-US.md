# Auth Service — OAuth2 / OIDC and SSO

Documentation of the Auth Service **OAuth2/OIDC issuer**, used for SSO of Grafana
(and other consumers) without exposing passwords.

## 1. Overview

The Auth Service can act as an **OAuth2/OIDC authorization server** (RFC 6749,
OIDC Core), issuing `id_token` (JWT RS256) and `access_token` for registered
clients (e.g. Grafana). User authentication is still performed against AD (LDAP)
or the local PostgreSQL.

Everything is **off by default**. To enable:

```yaml
auth:
  oauth2:
    enabled: true
    issuer: https://auth.example.com
    client-id: grafana
    client-secret: <secret>
    redirect-uris:
      - https://grafana.example.com/login/generic_oauth
```

Equivalent environment variables: `AUTH_OAUTH2_ENABLED`, `AUTH_OAUTH2_ISSUER`,
`AUTH_OAUTH2_CLIENT_ID`, `AUTH_OAUTH2_CLIENT_SECRET`, `AUTH_OAUTH2_REDIRECT_URIS`.

## 2. Endpoints

| Method | Route | Description |
|--------|-------|-------------|
| GET | `/oauth2/authorize` | Start of the flow (login by form against AD) |
| POST | `/oauth2/token` | Exchange `authorization_code` + PKCE for tokens |
| GET | `/oauth2/jwks` | Public key (JWKS) to validate the `id_token` |
| GET | `/userinfo` | User and groups (Bearer access token) |
| GET | `/oauth2/login` | Issuer login form |
| POST | `/oauth2/introspect` | Token introspection |
| POST | `/oauth2/revoke` | Token revocation |

## 3. Flow (Authorization Code + PKCE)

1. The consumer (Grafana) redirects the browser to `/oauth2/authorize` with
   `client_id`, `redirect_uri`, `response_type=code`, `scope=openid profile`,
   `state` and `code_challenge` (PKCE).
2. The Auth Service presents the login form (LDAP/AD or Postgres).
3. After authentication, it redirects back to `redirect_uri` with `code`.
4. The consumer exchanges the `code` for `id_token` + `access_token` at
   `/oauth2/token` (sending `code_verifier`).
5. The `id_token` (RS256) is validated with the public key from `/oauth2/jwks`.
6. `/userinfo` returns `sub`, `name`, `preferred_username`, `groups`.

## 4. `id_token` claims

In addition to the standard OIDC claims (`iss`, `sub`, `aud`, `exp`, `iat`,
`nonce`):

- `name` — display name
- `preferred_username` — login
- `groups` — AD groups (or Postgres)
- `grafana_role` — derived from groups (e.g. `Admin` when the user is admin)

The `grafana_role` claim lets Grafana map the role without reprocessing groups.

## 5. SSO by cookie (same host)

To avoid repeated logins between the ERP (port 8080), Grafana (port 3000) and the
Auth Service, the `sso_token` cookie (HttpOnly, SameSite=Lax, shared domain) is
written at login and read by the `SsoCookieCheckFilter` filter on
`/oauth2/authorize`. If the cookie exists and is valid, authorize approves
silently.

- Cookie: `sso_token`, HttpOnly, SameSite=Lax, 8 hours validity
- Filter: `SsoAutoConfig` (registered on `/oauth2/authorize`)
- Utility: `SsoCookieUtil` (set/clear)

## 6. Configuration

| Property | Env | Default | Description |
|----------|-----|---------|-------------|
| `auth.oauth2.enabled` | `AUTH_OAUTH2_ENABLED` | `false` | Turns the issuer on |
| `auth.oauth2.issuer` | `AUTH_OAUTH2_ISSUER` | — | Issuer URL (HTTPS in prod) |
| `auth.oauth2.client-id` | `AUTH_OAUTH2_CLIENT_ID` | `grafana` | Client ID |
| `auth.oauth2.client-secret` | `AUTH_OAUTH2_CLIENT_SECRET` | — | Client secret |
| `auth.oauth2.redirect-uris` | `AUTH_OAUTH2_REDIRECT_URIS` | — | Callback URIs (comma) |
| `auth.token.secret` | `AUTH_TOKEN_SECRET` | — | HS256 secret (≥32 bytes) for internal tokens |

The issuer's RSA key is generated automatically at `/etc/auth-service/issuer.key`
on first startup (or in the configured directory) and reused afterwards.

## 7. Security

- RSA-2048 key generated once and persisted to disk (not regenerated on every
  startup).
- `client_secret` in an environment variable, never in the repository.
- PKCE mandatory for the code flow.
- Short-lived tokens (id_token ~5 min, access_token configurable).
- CORS and CSRF disabled only on machine endpoints (`/oauth2/token`,
  `/oauth2/introspect`, etc.), which are called by Grafana, not by a form.
- `/userinfo` requires a valid Bearer access token (resource server with the
  issuer's own RSA key).

## 8. Grafana integration

In Grafana, configure generic OAuth:

```ini
[auth.generic_oauth]
enabled = true
client_id = grafana
client_secret = <AUTH_OAUTH2_CLIENT_SECRET>
scopes = openid profile
auth_url = https://<AUTH>/oauth2/authorize
token_url = https://<AUTH>/oauth2/token
api_url = https://<AUTH>/userinfo
use_pkce = true
```

The role in Grafana is defined by the `grafana_role` claim (Admin/Editor/Viewer).

## 9. Notes

- The issuer is **optional**: with `auth.oauth2.enabled=false` (default), the
  Auth Service keeps working as an identity validator (LDAP/AD/Postgres) without
  issuing tokens.
- The issuer's RSA key is independent of the HS256 secret used for internal
  tokens (`AUTH_TOKEN_SECRET`).
- Audit logs (MFA, login, issuance) are written by `AuditService`.