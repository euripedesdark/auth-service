# Auth Service — OAuth2 / OIDC y SSO

Documentación del **emisor OAuth2/OIDC** del Auth Service, usado para SSO de
Grafana (y de otros consumidores) sin exponer contraseñas.

## 1. Visión general

El Auth Service puede actuar como **servidor de autorización OAuth2/OIDC**
(RFC 6749, OIDC Core), emitiendo `id_token` (JWT RS256) y `access_token` para
clientes registrados (p. ej. Grafana). La autenticación del usuario sigue
haciéndose contra el AD (LDAP) o el PostgreSQL local.

Todo está **desactivado por defecto**. Para habilitar:

```yaml
auth:
  oauth2:
    enabled: true
    issuer: https://auth.example.com
    client-id: grafana
    client-secret: <secreto>
    redirect-uris:
      - https://grafana.example.com/login/generic_oauth
```

Variables de entorno equivalentes: `AUTH_OAUTH2_ENABLED`, `AUTH_OAUTH2_ISSUER`,
`AUTH_OAUTH2_CLIENT_ID`, `AUTH_OAUTH2_CLIENT_SECRET`, `AUTH_OAUTH2_REDIRECT_URIS`.

## 2. Endpoints

| Método | Ruta | Descripción |
|--------|------|-------------|
| GET | `/oauth2/authorize` | Inicio del flujo (login por formulario contra el AD) |
| POST | `/oauth2/token` | Cambio de `authorization_code` + PKCE por tokens |
| GET | `/oauth2/jwks` | Clave pública (JWKS) para validar el `id_token` |
| GET | `/userinfo` | Usuario y grupos (Bearer access token) |
| GET | `/oauth2/login` | Formulario de login del emisor |
| POST | `/oauth2/introspect` | Introspección de token |
| POST | `/oauth2/revoke` | Revocación de token |

## 3. Flujo (Authorization Code + PKCE)

1. El consumidor (Grafana) redirige el navegador a `/oauth2/authorize` con
   `client_id`, `redirect_uri`, `response_type=code`, `scope=openid profile`,
   `state` y `code_challenge` (PKCE).
2. El Auth Service presenta el formulario de login (LDAP/AD o Postgres).
3. Tras la autenticación, redirige de vuelta a `redirect_uri` con `code`.
4. El consumidor cambia el `code` por `id_token` + `access_token` en
   `/oauth2/token` (enviando `code_verifier`).
5. El `id_token` (RS256) se valida con la clave pública de `/oauth2/jwks`.
6. `/userinfo` devuelve `sub`, `name`, `preferred_username`, `groups`.

## 4. Claims del `id_token`

Además de los claims estándar de OIDC (`iss`, `sub`, `aud`, `exp`, `iat`,
`nonce`):

- `name` — nombre visible
- `preferred_username` — login
- `groups` — grupos del AD (o de Postgres)
- `grafana_role` — derivado de los grupos (p. ej. `Admin` si el usuario es admin)

El claim `grafana_role` permite a Grafana mapear el rol sin reprocesar grupos.

## 5. SSO por cookie (mismo host)

Para evitar logins repetidos entre el ERP (puerto 8080), Grafana (puerto 3000) y
el Auth Service, la cookie `sso_token` (HttpOnly, SameSite=Lax, dominio
compartido) se graba en el login y la lee el filtro `SsoCookieCheckFilter` en
`/oauth2/authorize`. Si la cookie existe y es válida, el authorize aprueba en
silencio.

- Cookie: `sso_token`, HttpOnly, SameSite=Lax, 8 horas de validez
- Filtro: `SsoAutoConfig` (registrado en `/oauth2/authorize`)
- Utilidad: `SsoCookieUtil` (set/clear)

## 6. Configuración

| Propiedad | Env | Por defecto | Descripción |
|-----------|-----|-------------|-------------|
| `auth.oauth2.enabled` | `AUTH_OAUTH2_ENABLED` | `false` | Enciende el emisor |
| `auth.oauth2.issuer` | `AUTH_OAUTH2_ISSUER` | — | URL del issuer (HTTPS en prod) |
| `auth.oauth2.client-id` | `AUTH_OAUTH2_CLIENT_ID` | `grafana` | ID del cliente |
| `auth.oauth2.client-secret` | `AUTH_OAUTH2_CLIENT_SECRET` | — | Secreto del cliente |
| `auth.oauth2.redirect-uris` | `AUTH_OAUTH2_REDIRECT_URIS` | — | URIs de callback (coma) |
| `auth.token.secret` | `AUTH_TOKEN_SECRET` | — | Secreto HS256 (≥32 bytes) para tokens internos |

La clave RSA del issuer se genera automáticamente en `/etc/auth-service/issuer.key`
en la primera subida (o en el directorio configurado) y se reutiliza después.

## 7. Seguridad

- Clave RSA-2048 generada una vez y persistida en disco (no se regenera en cada
  subida).
- `client_secret` en variable de entorno, nunca en el repositorio.
- PKCE obligatorio para el flujo de código.
- Tokens de corta duración (id_token ~5 min, access_token configurable).
- CORS y CSRF deshabilitados solo en los endpoints de máquina
  (`/oauth2/token`, `/oauth2/introspect`, etc.), que son llamados por Grafana, no
  por un formulario.
- `/userinfo` exige un Bearer access token válido (resource server con la clave
  RSA del propio issuer).

## 8. Integración con Grafana

En Grafana, configura el OAuth genérico:

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

El rol en Grafana se define por el claim `grafana_role` (Admin/Editor/Viewer).

## 9. Observaciones

- El emisor es **opcional**: con `auth.oauth2.enabled=false` (por defecto), el
  Auth Service sigue funcionando como validador de identidad (LDAP/AD/Postgres)
  sin emitir tokens.
- La clave RSA del issuer es independiente del secreto HS256 usado para tokens
  internos (`AUTH_TOKEN_SECRET`).
- Los logs de auditoría (MFA, login, emisión) los escribe `AuditService`.