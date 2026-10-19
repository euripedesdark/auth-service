# Auth Service — OAuth2 / OIDC et SSO

Documentation de l'**émetteur OAuth2/OIDC** de l'Auth Service, utilisé pour le
SSO de Grafana (et d'autres consommateurs) sans exposer de mot de passe.

## 1. Vue d'ensemble

L'Auth Service peut agir comme **serveur d'autorisation OAuth2/OIDC**
(RFC 6749, OIDC Core), émettant `id_token` (JWT RS256) et `access_token` pour
les clients enregistrés (ex. Grafana). L'authentification de l'utilisateur
continue d'être faite contre l'AD (LDAP) ou le PostgreSQL local.

Tout est **désactivé par défaut**. Pour activer :

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

Variables d'environnement équivalentes : `AUTH_OAUTH2_ENABLED`,
`AUTH_OAUTH2_ISSUER`, `AUTH_OAUTH2_CLIENT_ID`, `AUTH_OAUTH2_CLIENT_SECRET`,
`AUTH_OAUTH2_REDIRECT_URIS`.

## 2. Endpoints

| Méthode | Route | Description |
|---------|-------|-------------|
| GET | `/oauth2/authorize` | Début du flux (login par formulaire contre l'AD) |
| POST | `/oauth2/token` | Échange `authorization_code` + PKCE contre des tokens |
| GET | `/oauth2/jwks` | Clé publique (JWKS) pour valider l'`id_token` |
| GET | `/userinfo` | Utilisateur et groupes (Bearer access token) |
| GET | `/oauth2/login` | Formulaire de login de l'émetteur |
| POST | `/oauth2/introspect` | Introspection de token |
| POST | `/oauth2/revoke` | Révocation de token |

## 3. Flux (Authorization Code + PKCE)

1. Le consommateur (Grafana) redirige le navigateur vers `/oauth2/authorize`
   avec `client_id`, `redirect_uri`, `response_type=code`, `scope=openid profile`,
   `state` et `code_challenge` (PKCE).
2. L'Auth Service présente le formulaire de login (LDAP/AD ou Postgres).
3. Après authentification, il redirige vers `redirect_uri` avec `code`.
4. Le consommateur échange le `code` contre `id_token` + `access_token` sur
   `/oauth2/token` (en envoyant `code_verifier`).
5. L'`id_token` (RS256) est validé avec la clé publique de `/oauth2/jwks`.
6. `/userinfo` renvoie `sub`, `name`, `preferred_username`, `groups`.

## 4. Claims de l'`id_token`

En plus des claims OIDC standards (`iss`, `sub`, `aud`, `exp`, `iat`, `nonce`) :

- `name` — nom d'affichage
- `preferred_username` — login
- `groups` — groupes de l'AD (ou de Postgres)
- `grafana_role` — dérivé des groupes (ex. `Admin` si l'utilisateur est admin)

Le claim `grafana_role` permet à Grafana de mapper le rôle sans retraiter les
groupes.

## 5. SSO par cookie (même hôte)

Pour éviter les connexions répétées entre l'ERP (port 8080), Grafana (port 3000)
et l'Auth Service, le cookie `sso_token` (HttpOnly, SameSite=Lax, domaine
partagé) est écrit au login et lu par le filtre `SsoCookieCheckFilter` sur
`/oauth2/authorize`. Si le cookie existe et est valide, l'authorize approuve
silencieusement.

- Cookie : `sso_token`, HttpOnly, SameSite=Lax, validité 8 heures
- Filtre : `SsoAutoConfig` (enregistré sur `/oauth2/authorize`)
- Utilitaire : `SsoCookieUtil` (set/clear)

## 6. Configuration

| Propriété | Env | Par défaut | Description |
|-----------|-----|------------|-------------|
| `auth.oauth2.enabled` | `AUTH_OAUTH2_ENABLED` | `false` | Allume l'émetteur |
| `auth.oauth2.issuer` | `AUTH_OAUTH2_ISSUER` | — | URL de l'issuer (HTTPS en prod) |
| `auth.oauth2.client-id` | `AUTH_OAUTH2_CLIENT_ID` | `grafana` | ID du client |
| `auth.oauth2.client-secret` | `AUTH_OAUTH2_CLIENT_SECRET` | — | Secret du client |
| `auth.oauth2.redirect-uris` | `AUTH_OAUTH2_REDIRECT_URIS` | — | URIs de callback (virgule) |
| `auth.token.secret` | `AUTH_TOKEN_SECRET` | — | Secret HS256 (≥32 octets) pour les tokens internes |

La clé RSA de l'issuer est générée automatiquement dans
`/etc/auth-service/issuer.key` au premier démarrage (ou dans le répertoire
configuré) et réutilisée ensuite.

## 7. Sécurité

- Clé RSA-2048 générée une fois et persistée sur disque (pas régénérée à chaque
  démarrage).
- `client_secret` dans une variable d'environnement, jamais dans le dépôt.
- PKCE obligatoire pour le flux de code.
- Tokens de courte durée (id_token ~5 min, access_token configurable).
- CORS et CSRF désactivés uniquement sur les endpoints machine
  (`/oauth2/token`, `/oauth2/introspect`, etc.), appelés par Grafana, pas par un
  formulaire.
- `/userinfo` exige un Bearer access token valide (resource server avec la clé
  RSA de l'issuer lui-même).

## 8. Intégration avec Grafana

Dans Grafana, configurez l'OAuth générique :

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

Le rôle dans Grafana est défini par le claim `grafana_role` (Admin/Editor/Viewer).

## 9. Remarques

- L'émetteur est **optionnel** : avec `auth.oauth2.enabled=false` (par défaut),
  l'Auth Service continue de fonctionner comme validateur d'identité
  (LDAP/AD/Postgres) sans émettre de tokens.
- La clé RSA de l'issuer est indépendante du secret HS256 utilisé pour les
  tokens internes (`AUTH_TOKEN_SECRET`).
- Les journaux d'audit (MFA, login, émission) sont écrits par `AuditService`.