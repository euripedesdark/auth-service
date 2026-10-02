# BrasilCloud Auth Service

**Central authentication and identity service** — validates users against Active Directory (AD/LDAP) and returns identity + groups to ERP, firewall, proxy and other services. **REST + HTTP Basic** contract, with short-lived **Bearer JWT** for modern integrations. Not an OAuth2/OIDC server.

## About

Single authentication point of the BrasilCloud ecosystem (used by Brasil SaaS ERP). Authenticates to AD over LDAPS and returns identityId, username, provider and groups. AD groups are identity data: **authorization belongs to each consumer system**.

## Stack

| Layer | Technology |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 3.3.5 (Web, Security, Actuator) |
| Directory | Spring LDAP |
| Tokens | Short JWT (oauth2-jose) |
| API docs | springdoc-openapi (Swagger UI) |
| Build | Maven |
| Default port | `8181` |

## Run

```bash
export AUTH_LDAP_URL='ldaps://100.100.100.100:636'
export AUTH_LDAP_BASE='DC=homelab,DC=local'
export AUTH_LDAP_USER_DN='CN=svc-auth,OU=Service Accounts,DC=homelab,DC=local'
export AUTH_LDAP_PASSWORD='SERVICE_ACCOUNT_PASSWORD'
export AUTH_LDAP_USER_SEARCH_BASE='OU=Usuarios,DC=homelab,DC=local'

mvn clean package
java -jar target/auth-service-1.0.0-SNAPSHOT.jar
```

Dev: `mvn spring-boot:run`. Port: `SERVER_PORT=8181`. In production run behind HTTPS/reverse proxy; do not expose 8181.

## Configuration

| Variable | Default | Purpose |
|---|---|---|
| `AUTH_LDAP_URL` | — | Secure LDAP endpoint |
| `AUTH_LDAP_BASE` | — | LDAP base |
| `AUTH_LDAP_USER_DN` | — | Service account (non-admin) |
| `AUTH_LDAP_PASSWORD` | — | Account password (**out of Git**) |
| `AUTH_LDAP_USER_SEARCH_BASE` | — | User search base |
| `AUTH_LDAP_REQUIRE_SECURE` | `true` | Require `ldaps://` |
| `AUTH_TOKEN_SECRET` | — | JWT key (32+ bytes, out of Git) |
| `AUTH_TOKEN_TTL_SECONDS` | `900` | JWT lifetime |
| `SERVER_PORT` | `8181` | HTTP port |

## Endpoints

| Method | Route | Use |
|---|---|---|
| `POST` | `/api/v1/identity/authenticate` | Login |
| `POST` | `/api/v1/identity/token` | Login returning Bearer JWT |
| `GET` | `/api/v1/identity/me` | Identity |
| `GET` | `/api/v1/identity/groups` | User groups |
| `GET` | `/actuator/health` | Health (public) |
| `GET` | `/swagger-ui.html` | Interactive docs |

`401` = bad credentials, `503` = AD/service unavailable (never treat 503 as wrong password). Never log AD passwords or JWTs.

## Deployment and Integration Guides

| Language | Guide |
|---|---|
| PT-BR | docs/GUIA-INTEGRACAO-AUTH-SERVICE.pt-BR.md |
| EN-US | docs/GUIA-INTEGRACAO-AUTH-SERVICE.en-US.md |
| ES-ES | docs/GUIA-INTEGRACAO-AUTH-SERVICE.es-ES.md |
| FR-FR | docs/GUIA-INTEGRACAO-AUTH-SERVICE.fr-FR.md |

## Requirements: LDAP / LDAPS / Active Directory

- **Linux:** Samba Active Directory — full guide:
  https://medium.com/meetcyber/full-guide-deploying-samba-active-directory-on-fedora-and-rocky-with-hybrid-dns-and-gpo-support-30f47fab1e90
- **Windows:** Windows Server Active Directory with LDAPS enabled.

It can be evaluated without the full corporate environment; in production use it integrated with an LDAP/LDAPS directory, with a non-admin service account.

## Sponsor the Project

BrasilCloud Auth Service is open source maintained by a single developer. If it helped you, your company or your team, consider supporting it.

**PIX (Brazil only):**

```
24adc62c-b073-4587-974d-03fe35f6733f
```

Any amount pays for servers and certificates. It does not buy SLA on any issue.

### International transfer

PIX does not work outside Brazil. **From a US bank**, domestic transfer. **From anywhere else**, international Swift transfer.

| | |
|---|---|
| **Name** | Euripedes Batista de Paiva Junior |
| **Account type** | Checking |
| **Routing number** (wire and ACH) | `101019628` |
| **Account number** | `215822927677` |
| **Bank** | Wise US Inc, 108 W 13th St, Wilmington, DE, 19801, United States |
| **SWIFT/BIC** | `TRWIUS35XXX` |

## Support

- Email: euripedesdark@gmail.com
- GitHub Issues: https://github.com/euripedesdark/auth-service/issues
- Docs: `docs/`

## How to Contribute

1. Open an issue before writing code.
2. Create a descriptive branch.
3. Commit by topic and run tests (`mvn test`).
4. Open the Pull Request against `main`.

## License

This project is under **GNU AGPL v3**. See `LICENSE.en-US.md` (official English text, also `LICENSE.md`).

(c) Euripedes Batista de Paiva Junior
