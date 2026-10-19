# Auth Service — Identidad y Autenticación

Documentación de los **endpoints de identidad** del Auth Service: autenticación
contra AD/Postgres, emisión de token corto y resolución de dominio.

## 1. Visión general

El Auth Service valida credenciales contra:

- **Active Directory** (LDAP/LDAPS) — provider `AD`
- **PostgreSQL** local — provider `POSTGRES` (un role con SUPERUSER recibe
  `POSTGRES_SUPERUSER`)
- **Otro AD por nombre de dominio** — vía `authenticate-domain` (resuelve el
  dominio y conecta al AD correspondiente)

Una autenticación exitosa puede grabar la cookie `sso_token` (cuando se llama
vía `/api/v1/identity/authenticate`), permitiendo SSO con Grafana/ERP en el
mismo host.

## 2. Endpoints de identidad (`/api/v1/identity`)

| Método | Ruta | Auth | Descripción |
|--------|------|------|-------------|
| POST | `/authenticate` | — | Autentica contra AD/Postgres y graba la cookie SSO |
| POST | `/token` | — | Autentica y emite Bearer token corto (JSON) |
| GET | `/me` | Bearer/sesión | Devuelve la identidad autenticada |
| GET | `/groups` | Bearer/sesión | Identidad + grupos |
| GET | `/provider` | Bearer/sesión | Identidad + provider usado |
| GET | `/authenticated` | Bearer/sesión | Identidad autenticada (booleano implícito) |
| POST | `/authenticate-domain` | — | Autentica en otro AD por nombre de dominio |
| GET | `/resolve-domain?domain=` | — | Muestra dónde resuelve el dominio (DNS/LDAPS) |

## 3. Contrato de petición

### `/authenticate` y `/token`

```json
{ "username": "juan", "password": "••••", "provider": "AD" }
```

- `provider`: `AD` (por defecto) o `POSTGRES`.
- Respuesta de `/authenticate`: `IdentityDto` (username, provider, groups) +
  cookie `sso_token`.
- Respuesta de `/token`: `{ "accessToken": "...", "tokenType": "Bearer",
  "expiresIn": 300 }`.

### `/authenticate-domain`

```json
{ "username": "juan", "password": "••••", "domain": "srvcloud.cloud" }
```

Resuelve el dominio vía DNS y autentica en el AD correspondiente.

### `/resolve-domain?domain=srvcloud.cloud`

Devuelve `{ domain, ips, ldapUrl, baseDn }` o 404 si no se resuelve.

## 4. Identidad y grupos

- `IdentityDto`: `{ username, provider, groups[] }`.
- En AD, los grupos vienen de `memberOf` (y el grupo primario `Domain Users`
  cuando `primaryGroupID=513`).
- En Postgres, el grupo `POSTGRES_SUPERUSER` se agrega solo si el role tiene
  `rolsuper` (ver `POSTGRES_SUPERUSER.md`).
- La identidad puede resolverse de un JWT Bearer (emisor propio) o de la
  sesión HTTP.

## 5. Páginas servidas

| Ruta | Descripción |
|------|-------------|
| `/admin`, `/admin/` | Panel del administrador (emisión/revocación/MFA) |
| `/mfa`, `/mfa/` | Flujo del usuario (MFA + descarga de certificado) |
| `/oauth2/login` | Formulario de login del emisor OAuth2 |

## 6. Errores

| Status | Causa |
|--------|-------|
| 401 | Credenciales inválidas (`BadCredentialsException`) |
| 503 | Provider no disponible (`ProviderUnavailableException`) |
| 404 | Dominio no resuelto (`resolve-domain`) |

## 7. Seguridad

- Las contraseñas nunca se registran; solo el resultado (éxito/fallo) se audita.
- La cookie `sso_token` es HttpOnly, SameSite=Lax, válida 8 horas.
- `/me`, `/groups`, `/provider`, `/authenticated` exigen autenticación (Bearer o
  sesión).
- `/authenticate`, `/token`, `/authenticate-domain`, `/resolve-domain` son
  públicos (usados por el login).