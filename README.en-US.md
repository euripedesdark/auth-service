# Astral Auth Service

**Central Identity, Authentication, and Linux Privilege Management Service** — Built with **Rust (Axum / Tokio)** using OOP architecture, natively integrated with Samba 4 Active Directory DC over LDAPS, short-lived JWT token issuer, and centralized host OS privilege (`wheel`/`sudo`) manager for the Astral platform.

---

## 🌟 Key Features

1. **Centralized Enterprise Authentication**:
   - Secure connection over LDAPS (`ldaps://127.0.0.1:636`) with TLS.
   - LDAP search for users and groups (`memberOf`), password validation with user bind.
   - HMAC-SHA256 signed JWT tokens containing `identityId`, `username`, `provider`, `groups`, `isAdmin`, and `hasWheel`.

2. **Linux Privilege Verification & Synchronization Service (`wheel`)**:
   - **Samba Active Directory Audit**: Dynamically discovers administrator accounts (`Administrator`, members of `Domain Admins`, `Administrators`, and `Enterprise Admins`).
   - **Linux Host OS Verification**: Inspects `/etc/group`, `/etc/passwd`, and NSS (`getent group wheel`, `id -nG`) to verify whether AD admins have Linux `wheel` permissions.
   - **Automated Synchronization**: Seamlessly adds corporate admins to the `wheel` group (`usermod -aG wheel`) or provisions them locally (`useradd -M -s /bin/bash -G wheel`), ensuring operational compliance.
   - **BIND AD-DNS Module Integration**: Validates users originating from LDAP queries and binds in the `astral-ad-dns` module, enforcing the same verification and compliance rules.

3. **Strict PostgreSQL Superuser Isolation**:
   - PostgreSQL database superusers (`rolsuper = true`) are strictly restricted to the database tier.
   - Absolute prohibition against assigning `wheel` group privileges or Linux terminal access to database users (`HTTP 403 Forbidden`).

---

## 🛠️ Technology Stack

| Layer | Technology |
|---|---|
| Language | Rust 2021 (v1.99+) |
| Web Framework | Axum 0.8 / Tokio Async Runtime |
| Directory / LDAP | `ldap3` (Secure LDAPS) |
| Cryptography / Tokens | `jsonwebtoken` 11.1 / `chrono` |
| OS Integration | `/etc/group`, `/etc/passwd`, `getent`, `usermod`, `useradd` |
| Admin Web UI | React 19 / PrimeReact / Vite (i18n: pt-BR, en-US, es-ES, fr-FR) |
| Service Port | `8181` (Reverse TLS Nginx on port `8443`) |

---

## 🚀 REST API Endpoints

### Authentication & Session
- `POST /api/v1/identity/authenticate`: Validates credentials and sets SSO session cookies.
- `POST /api/v1/identity/token`: Validates credentials and returns Bearer JWT token with `Identity` object.
- `GET /api/v1/identity/me`: Validates Bearer token from headers and returns claims.
- `POST /api/v1/identity/logout`: Clears session cookies.
- `GET /health` & `/actuator/health`: Service health check and Samba AD status.

### Linux Privilege Management (Wheel / Samba AD & BIND)
- `GET /api/v1/privileges/status`: Returns comprehensive Samba AD audit report, Linux `wheel` members, and admin compliance status.
- `GET /api/v1/privileges/verify?username={user}&provider={prov}`: Verifies Linux `wheel` status for a specific user.
- `POST /api/v1/privileges/verify-bind`: Validates users bound via the `astral-ad-dns` module.
- `POST /api/v1/privileges/sync-wheel`: Synchronizes an AD admin user into the Linux host `wheel` group.

---

## 🔒 Audit and Sync Examples via cURL

```bash
# Check audit status
curl -s http://127.0.0.1:8181/api/v1/privileges/status | jq .

# Verify specific user permission
curl -s "http://127.0.0.1:8181/api/v1/privileges/verify?username=Administrator" | jq .

# Sync AD admin into Linux wheel group
curl -s -X POST http://127.0.0.1:8181/api/v1/privileges/sync-wheel \
  -H "Content-Type: application/json" \
  -d '{"username":"Administrator","createLinuxUserIfMissing":true}' | jq .
```
