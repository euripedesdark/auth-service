# Auth Service — Identidade e Autenticação

Documentação dos **endpoints de identidade** do Auth Service: autenticação
contra AD/Postgres, emissão de token curto e resolução de domínio.

## 1. Visão geral

O Auth Service valida credenciais contra:

- **Active Directory** (LDAP/LDAPS) — provider `AD`
- **PostgreSQL** local — provider `POSTGRES` (role com SUPERUSER recebe
  `POSTGRES_SUPERUSER`)
- **Outro AD por domínio DNS** — via `authenticate-domain` (resolve o domínio
  e conecta no AD correspondente)

A autenticação bem-sucedida pode gravar o cookie `sso_token` (quando chamada
via `/api/v1/identity/authenticate`), permitindo SSO com Grafana/ERP no mesmo
host.

## 2. Endpoints de identidade (`/api/v1/identity`)

| Método | Rota | Auth | Descrição |
|--------|------|------|-----------|
| POST | `/authenticate` | — | Autentica no AD/Postgres e grava cookie SSO |
| POST | `/token` | — | Autentica e emite Bearer token curto (JSON) |
| GET | `/me` | Bearer/sessão | Retorna a identidade autenticada |
| GET | `/groups` | Bearer/sessão | Identidade + grupos |
| GET | `/provider` | Bearer/sessão | Identidade + provider usado |
| GET | `/authenticated` | Bearer/sessão | Identidade autenticada (booleano implícito) |
| POST | `/authenticate-domain` | — | Autentica em outro AD pelo nome do domínio |
| GET | `/resolve-domain?domain=` | — | Mostra onde o domínio resolve (DNS/LDAPS) |

## 3. Contrato de requisição

### `/authenticate` e `/token`

```json
{ "username": "joao", "password": "••••", "provider": "AD" }
```

- `provider`: `AD` (default) ou `POSTGRES`.
- Resposta de `/authenticate`: `IdentityDto` (username, provider, groups) +
  cookie `sso_token`.
- Resposta de `/token`: `{ "accessToken": "...", "tokenType": "Bearer",
  "expiresIn": 300 }`.

### `/authenticate-domain`

```json
{ "username": "joao", "password": "••••", "domain": "srvcloud.cloud" }
```

Resolve o domínio via DNS e autentica no AD correspondente.

### `/resolve-domain?domain=srvcloud.cloud`

Retorna `{ domain, ips, ldapUrl, baseDn }` ou 404 se não resolvido.

## 4. Identidade e grupos

- `IdentityDto`: `{ username, provider, groups[] }`.
- No AD, os grupos vêm de `memberOf` (e o grupo primário `Domain Users` quando
  `primaryGroupID=513`).
- No Postgres, o grupo `POSTGRES_SUPERUSER` é adicionado apenas se a role tiver
  `rolsuper` (ver `POSTGRES_SUPERUSER.md`).
- A identidade pode ser resolvida de um JWT Bearer (issuer próprio) ou da
  sessão HTTP.

## 5. Páginas servidas

| Rota | Descrição |
|------|-----------|
| `/admin`, `/admin/` | Painel do administrador (emissão/revogação/MFA) |
| `/mfa`, `/mfa/` | Fluxo do usuário (MFA + download de certificado) |
| `/oauth2/login` | Formulário de login do issuer OAuth2 |

## 6. Erros

| Status | Causa |
|--------|-------|
| 401 | Credenciais inválidas (`BadCredentialsException`) |
| 503 | Provider indisponível (`ProviderUnavailableException`) |
| 404 | Domínio não resolvido (`resolve-domain`) |

## 7. Segurança

- Senhas nunca são logadas; apenas o resultado (sucesso/falha) é auditado.
- O cookie `sso_token` é HttpOnly, SameSite=Lax, válido por 8 horas.
- Endpoints `/me`, `/groups`, `/provider`, `/authenticated` exigem autenticação
  (Bearer ou sessão).
- `/authenticate`, `/token`, `/authenticate-domain`, `/resolve-domain` são públicos
  (usados pelo login).