# Astral Auth Service

**Serviço Central de Identidade, Autenticação e Gestão de Privilégios Linux** — Desenvolvido em **Rust (Axum / Tokio)** com arquitetura orientada a objetos, integrado nativamente ao Samba 4 Active Directory DC via LDAPS, emissor de tokens JWT de curta duração e gestor centralizado de privilégios de sistema operacional (`wheel`/`sudo`) para a plataforma Astral.

---

## 🌟 Funcionalidades Principais

1. **Autenticação Centralizada Corporativa**:
   - Conexão segura via LDAPS (`ldaps://127.0.0.1:636`) com TLS.
   - Suporte a consultas LDAP de usuários, grupos (`memberOf`) e validação de senhas com bind de usuário.
   - Emissão de tokens JWT assinados com HMAC-SHA256 contendo `identityId`, `username`, `provider`, `groups`, `isAdmin` e `hasWheel`.

2. **Serviço de Verificação e Sincronização de Privilégios Linux (`wheel`)**:
   - **Auditoria do Samba Active Directory**: Identifica dinamicamente contas de administradores (`Administrator`, membros de `Domain Admins`, `Administrators` e `Enterprise Admins`).
   - **Verificação no Host Linux**: Checa em `/etc/group`, `/etc/passwd` e subsistema NSS (`getent group wheel`, `id -nG`) se os administradores do AD possuem permissão no grupo `wheel` do Linux.
   - **Sincronização Automatizada**: Permite adicionar administradores corporativos ao grupo `wheel` (`usermod -aG wheel`) ou criá-los automaticamente (`useradd -M -s /bin/bash -G wheel`), garantindo conformidade operacional.
   - **Integração com o Módulo BIND AD-DNS**: Valida usuários originados por consultas e binds LDAP do módulo `astral-ad-dns`, aplicando as mesmas regras de verificação e conformidade.

3. **Política Estrita de Isolamento PostgreSQL**:
   - Superusuários do banco de dados PostgreSQL (`rolsuper = true`) possuem acesso estritamente restrito à camada de dados.
   - Bloqueio intransigente contra atribuição de privilégios `wheel` ou acesso a terminal Linux para usuários de banco de dados (`HTTP 403 Forbidden`).

---

## 🛠️ Stack Tecnológica

| Camada | Tecnologia |
|---|---|
| Linguagem | Rust 2021 (v1.99+) |
| Framework Web | Axum 0.8 / Tokio Async Runtime |
| Diretório LDAP | `ldap3` (LDAPS seguro) |
| Criptografia / Tokens | `jsonwebtoken` 11.1 / `chrono` |
| Integração SO | `/etc/group`, `/etc/passwd`, `getent`, `usermod`, `useradd` |
| Interface Administrativa | React 19 / PrimeReact / Vite (i18n: pt-BR, en-US, es-ES, fr-FR) |
| Porta de Serviço | `8181` (Nginx TLS reverso na porta `8443`) |

---

## 🚀 Endpoints da API REST

### Autenticação e Sessão
- `POST /api/v1/identity/authenticate`: Autentica credenciais e retorna cookies de sessão (`sso_token`, `astral_token`).
- `POST /api/v1/identity/token`: Autentica e retorna JSON com token Bearer JWT e objeto `Identity`.
- `GET /api/v1/identity/me`: Extrai e valida o token Bearer dos headers, retornando as claims do usuário.
- `POST /api/v1/identity/logout`: Invalida os cookies da sessão.
- `GET /health` e `/actuator/health`: Healthcheck do serviço e status do Samba AD.

### Gestão de Privilégios Linux (Wheel / Samba AD & BIND)
- `GET /api/v1/privileges/status`: Retorna o relatório completo de auditoria do Samba AD, membros do grupo `wheel` do Linux e status de conformidade de cada administrador.
- `GET /api/v1/privileges/verify?username={user}&provider={prov}`: Verifica pontualmente se um usuário possui privilégio `wheel` no Linux.
- `POST /api/v1/privileges/verify-bind`: Valida usuários originários do módulo `astral-ad-dns` via bind LDAP.
- `POST /api/v1/privileges/sync-wheel`: Sincroniza o usuário administrador do AD no grupo `wheel` do Linux host.

---

## 🔒 Auditoria e Sincronização via cURL

```bash
# Verificar status da auditoria
curl -s http://127.0.0.1:8181/api/v1/privileges/status | jq .

# Verificar permissão de um usuário específico
curl -s "http://127.0.0.1:8181/api/v1/privileges/verify?username=Administrator" | jq .

# Sincronizar administrador do AD no grupo wheel do Linux
curl -s -X POST http://127.0.0.1:8181/api/v1/privileges/sync-wheel \
  -H "Content-Type: application/json" \
  -d '{"username":"Administrator","createLinuxUserIfMissing":true}' | jq .
```

## OAuth2 Authorization Code + PKCE

O auth-service inclui autorizacao por codigo com PKCE S256, login corporativo
via LDAPS, troca de codigo, userinfo e sessao SSO. O HCI cadastra e implanta
os dois servicos com `configure_oauth2.py`.

[Configuracao, endpoints e deploy OAuth2](docs/oauth2.md).


## Atualizar backend e frontend

Na primeira vez, atualize o script no checkout do **auth-service**:

```bash
git pull --ff-only origin main
python3 deploy_auth.py --install-deps
```

Depois, basta executar `python3 deploy_auth.py`. Ele faz `git pull --ff-only
origin main`, recarrega o script atualizado, testa/compila o Rust e executa
`npm ci` e `npm run build` em `web`. Use Node.js 22.12+ ou 24 e Rust stable.
`--install-deps` instala dependencias com dnf/yum no Fedora/RHEL ou apt no
Debian/Ubuntu. O script solicita sudo e usa o usuario original para Git e builds.

O servico continua ativo durante os builds. So depois ele para a unit existente,
publica backend e frontend na mesma release e inicia o servico. A verificacao
confere health, metadados OAuth2/PKCE e o HTML em `/admin/`; se a ativacao falhar,
restaura a unit e a release anteriores. O frontend tambem e servido em `/mfa/`.
Preserva `auth.env`, `oauth2.env`, porta 8181 e configuracao nginx/TLS.
`--no-pull` permite implantar o checkout atual, por exemplo em CI.
Este comando atualiza o auth-service; nao provisiona Samba nem altera o HCI.
