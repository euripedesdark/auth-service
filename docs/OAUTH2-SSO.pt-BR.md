# Auth Service — OAuth2 / OIDC e SSO

Documentação do **emissor OAuth2/OIDC** do Auth Service, usado para SSO do
Grafana (e de outros consumidores) sem expor senha.

## 1. Visão geral

O Auth Service pode atuar como **servidor de autorização OAuth2/OIDC** (RFC 6749,
OIDC Core), emitindo `id_token` (JWT RS256) e `access_token` para clientes
registrados (ex.: Grafana). A autenticação do usuário continua sendo feita contra
o AD (LDAP) ou o PostgreSQL local.

Tudo está **desligado por padrão**. Para habilitar:

```yaml
auth:
  oauth2:
    enabled: true
    issuer: https://auth.example.com
    client-id: grafana
    client-secret: <segredo>
    redirect-uris:
      - https://grafana.example.com/login/generic_oauth
```

Variáveis de ambiente equivalentes: `AUTH_OAUTH2_ENABLED`, `AUTH_OAUTH2_ISSUER`,
`AUTH_OAUTH2_CLIENT_ID`, `AUTH_OAUTH2_CLIENT_SECRET`, `AUTH_OAUTH2_REDIRECT_URIS`.

## 2. Endpoints

| Método | Rota | Descrição |
|--------|------|-----------|
| GET | `/oauth2/authorize` | Início do fluxo (login por formulário contra o AD) |
| POST | `/oauth2/token` | Troca `authorization_code` + PKCE por tokens |
| GET | `/oauth2/jwks` | Chave pública (JWKS) para validar o `id_token` |
| GET | `/userinfo` | Usuário e grupos (Bearer access token) |
| GET | `/oauth2/login` | Formulário de login do issuer |
| POST | `/oauth2/introspect` | Introspecção de token |
| POST | `/oauth2/revoke` | Revogação de token |

## 3. Fluxo (Authorization Code + PKCE)

1. O consumidor (Grafana) redireciona o navegador para `/oauth2/authorize`
   com `client_id`, `redirect_uri`, `response_type=code`, `scope=openid profile`,
   `state` e `code_challenge` (PKCE).
2. O Auth Service apresenta o formulário de login (LDAP/AD ou Postgres).
3. Após autenticação, redireciona de volta para `redirect_uri` com `code`.
4. O consumidor troca o `code` por `id_token` + `access_token` em `/oauth2/token`
   (enviando `code_verifier`).
5. O `id_token` (RS256) é validado com a chave pública de `/oauth2/jwks`.
6. `/userinfo` retorna `sub`, `name`, `preferred_username`, `groups`.

## 4. Claims do `id_token`

Além dos claims padrão OIDC (`iss`, `sub`, `aud`, `exp`, `iat`, `nonce`):

- `name` — nome de exibição
- `preferred_username` — login
- `groups` — grupos do AD (ou do Postgres)
- `grafana_role` — derivado dos grupos (ex.: `Admin` quando o usuário é admin)

O claim `grafana_role` permite ao Grafana mapear o papel sem reprocessar grupos.

## 5. SSO por cookie (mesmo host)

Para evitar login repetido entre ERP (porta 8080), Grafana (porta 3000) e o
Auth Service, o cookie `sso_token` (HttpOnly, SameSite=Lax, domínio compartilhado)
é gravado no login e lido pelo filtro `SsoCookieCheckFilter` em
`/oauth2/authorize`. Se o cookie existir e for válido, o authorize aprova
silenciosamente.

- Cookie: `sso_token`, HttpOnly, SameSite=Lax, 8 horas de validade
- Filtro: `SsoAutoConfig` (registrado em `/oauth2/authorize`)
- Utilitário: `SsoCookieUtil` (set/clear)

## 6. Configuração

| Propriedade | Env | Padrão | Descrição |
|-------------|-----|--------|-----------|
| `auth.oauth2.enabled` | `AUTH_OAUTH2_ENABLED` | `false` | Liga o emissor |
| `auth.oauth2.issuer` | `AUTH_OAUTH2_ISSUER` | — | URL do issuer (deve ser HTTPS em prod) |
| `auth.oauth2.client-id` | `AUTH_OAUTH2_CLIENT_ID` | `grafana` | ID do cliente |
| `auth.oauth2.client-secret` | `AUTH_OAUTH2_CLIENT_SECRET` | — | Segredo do cliente |
| `auth.oauth2.redirect-uris` | `AUTH_OAUTH2_REDIRECT_URIS` | — | URIs de callback (vírgula) |
| `auth.token.secret` | `AUTH_TOKEN_SECRET` | — | Segredo HS256 (≥32 bytes) para tokens internos |

A chave RSA do issuer é gerada automaticamente em `/etc/auth-service/issuer.key`
na primeira subida (ou no diretório configurado) e reutilizada nas próximas.

## 7. Segurança

- Chave RSA-2048 gerada uma vez e persistida em disco (não regenerada a cada
  subida).
- `client_secret` em variável de ambiente, nunca no repositório.
- PKCE obrigatório para o fluxo de código.
- Tokens de curta duração (id_token ~5 min, access_token configurável).
- CORS e CSRF desabilitados apenas nos endpoints de máquina (`/oauth2/token`,
  `/oauth2/introspect`, etc.), que são chamados pelo Grafana, não por formulário.
- `/userinfo` exige Bearer access token válido (resource server com a chave RSA
  do próprio issuer).

## 8. Integração com Grafana

No Grafana, configure o OAuth genérico:

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

O papel no Grafana é definido pelo claim `grafana_role` (Admin/Editor/Viewer).

## 9. Observações

- O emissor é **opcional**: com `auth.oauth2.enabled=false` (padrão), o Auth
  Service continua funcionando como validador de identidade (LDAP/AD/Postgres)
  sem emitir tokens.
- A chave RSA do issuer é independente do segredo HS256 usado para tokens
  internos (`AUTH_TOKEN_SECRET`).
- Logs de auditoria (MFA, login, emissão) são gravados pelo `AuditService`.