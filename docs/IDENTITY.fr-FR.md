# Auth Service — Identité et Authentification

Documentation des **endpoints d'identité** de l'Auth Service : authentification
contre l'AD/Postgres, émission de token court et résolution de domaine.

## 1. Vue d'ensemble

L'Auth Service valide les credentials contre :

- **Active Directory** (LDAP/LDAPS) — provider `AD`
- **PostgreSQL** local — provider `POSTGRES` (un rôle avec SUPERUSER reçoit
  `POSTGRES_SUPERUSER`)
- **Un autre AD par nom de domaine** — via `authenticate-domain` (résout le
  domaine et se connecte à l'AD correspondant)

Une authentification réussie peut écrire le cookie `sso_token` (quand appelé
via `/api/v1/identity/authenticate`), permettant le SSO avec Grafana/ERP sur le
même hôte.

## 2. Endpoints d'identité (`/api/v1/identity`)

| Méthode | Route | Auth | Description |
|---------|-------|------|-------------|
| POST | `/authenticate` | — | Authentifie contre AD/Postgres et écrit le cookie SSO |
| POST | `/token` | — | Authentifie et émet un Bearer token court (JSON) |
| GET | `/me` | Bearer/session | Renvoie l'identité authentifiée |
| GET | `/groups` | Bearer/session | Identité + groupes |
| GET | `/provider` | Bearer/session | Identité + provider utilisé |
| GET | `/authenticated` | Bearer/session | Identité authentifiée (booléen implicite) |
| POST | `/authenticate-domain` | — | Authentifie dans un autre AD par nom de domaine |
| GET | `/resolve-domain?domain=` | — | Montre où le domaine se résout (DNS/LDAPS) |

## 3. Contrat de requête

### `/authenticate` et `/token`

```json
{ "username": "jean", "password": "••••", "provider": "AD" }
```

- `provider` : `AD` (par défaut) ou `POSTGRES`.
- Réponse de `/authenticate` : `IdentityDto` (username, provider, groups) +
  cookie `sso_token`.
- Réponse de `/token` : `{ "accessToken": "...", "tokenType": "Bearer",
  "expiresIn": 300 }`.

### `/authenticate-domain`

```json
{ "username": "jean", "password": "••••", "domain": "srvcloud.cloud" }
```

Résout le domaine via DNS et authentifie dans l'AD correspondant.

### `/resolve-domain?domain=srvcloud.cloud`

Renvoie `{ domain, ips, ldapUrl, baseDn }` ou 404 si non résolu.

## 4. Identité et groupes

- `IdentityDto` : `{ username, provider, groups[] }`.
- Dans l'AD, les groupes viennent de `memberOf` (et le groupe primaire
  `Domain Users` quand `primaryGroupID=513`).
- Dans Postgres, le groupe `POSTGRES_SUPERUSER` est ajouté uniquement si le rôle
  a `rolsuper` (voir `POSTGRES_SUPERUSER.md`).
- L'identité peut être résolue d'un JWT Bearer (émetteur propre) ou de la
  session HTTP.

## 5. Pages servies

| Route | Description |
|-------|-------------|
| `/admin`, `/admin/` | Panneau de l'administrateur (émission/révocation/MFA) |
| `/mfa`, `/mfa/` | Flux de l'utilisateur (MFA + téléchargement de certificat) |
| `/oauth2/login` | Formulaire de login de l'émetteur OAuth2 |

## 6. Erreurs

| Status | Cause |
|--------|-------|
| 401 | Identifiants invalides (`BadCredentialsException`) |
| 503 | Provider indisponible (`ProviderUnavailableException`) |
| 404 | Domaine non résolu (`resolve-domain`) |

## 7. Sécurité

- Les mots de passe ne sont jamais journalisés ; seul le résultat
  (succès/échec) est audité.
- Le cookie `sso_token` est HttpOnly, SameSite=Lax, valide 8 heures.
- `/me`, `/groups`, `/provider`, `/authenticated` exigent une authentification
  (Bearer ou session).
- `/authenticate`, `/token`, `/authenticate-domain`, `/resolve-domain` sont publics
  (utilisés par le login).