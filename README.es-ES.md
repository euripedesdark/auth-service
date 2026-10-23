# Astral Auth Service

**Servicio Central de Identidad, Autenticación y Gestión de Privilegios Linux** — Desarrollado en **Rust (Axum / Tokio)** con arquitectura orientada a objetos, integrado de forma nativa con Samba 4 Active Directory DC mediante LDAPS, emisor de tokens JWT de corta duración y gestor centralizado de privilegios del sistema operativo (`wheel`/`sudo`) para la plataforma Astral.

---

## 🌟 Características Principales

1. **Autenticación Centralizada Corporativa**:
   - Conexión segura mediante LDAPS (`ldaps://127.0.0.1:636`) con TLS.
   - Búsqueda LDAP de usuarios, grupos (`memberOf`) y validación de contraseñas con enlace (bind) de usuario.
   - Emisión de tokens JWT firmados con HMAC-SHA256 que contienen `identityId`, `username`, `provider`, `groups`, `isAdmin` y `hasWheel`.

2. **Servicio de Verificación y Sincronización de Privilegios Linux (`wheel`)**:
   - **Auditoría de Samba Active Directory**: Descubre dinámicamente cuentas de administradores (`Administrator`, miembros de `Domain Admins`, `Administrators` y `Enterprise Admins`).
   - **Verificación en el Host Linux**: Inspecciona `/etc/group`, `/etc/passwd` y NSS (`getent group wheel`, `id -nG`) para verificar si los administradores de AD tienen permisos en el grupo `wheel` de Linux.
   - **Sincronización Automatizada**: Permite añadir administradores corporativos al grupo `wheel` (`usermod -aG wheel`) o crearlos localmente (`useradd -M -s /bin/bash -G wheel`), garantizando el cumplimiento operativo.
   - **Integración con el Módulo BIND AD-DNS**: Valida usuarios originados por consultas y enlaces LDAP del módulo `astral-ad-dns`, aplicando las mismas reglas de verificación y cumplimiento.

3. **Política Estricta de Aislamiento PostgreSQL**:
   - Los superusuarios de la base de datos PostgreSQL (`rolsuper = true`) están estrictamente restringidos a la capa de datos.
   - Bloqueo intransigente contra la asignación de privilegios `wheel` o acceso al terminal Linux para usuarios de bases de datos (`HTTP 403 Forbidden`).

---

## 🛠️ Stack Tecnológico

| Capa | Tecnología |
|---|---|
| Lenguaje | Rust 2021 (v1.99+) |
| Framework Web | Axum 0.8 / Tokio Async Runtime |
| Directorio LDAP | `ldap3` (LDAPS seguro) |
| Criptografía / Tokens | `jsonwebtoken` 11.1 / `chrono` |
| Integración SO | `/etc/group`, `/etc/passwd`, `getent`, `usermod`, `useradd` |
| Panel Web Admin | React 19 / PrimeReact / Vite (i18n: pt-BR, en-US, es-ES, fr-FR) |
| Puerto del Servicio | `8181` (Nginx TLS inverso en puerto `8443`) |

---

## 🚀 Endpoints de la API REST

### Autenticación y Sesión
- `POST /api/v1/identity/authenticate`: Autentica credenciales y establece cookies de sesión SSO.
- `POST /api/v1/identity/token`: Autentica y devuelve token Bearer JWT con objeto `Identity`.
- `GET /api/v1/identity/me`: Extrae y valida el token Bearer devolviendo las claims.
- `POST /api/v1/identity/logout`: Invalida las cookies de sesión.
- `GET /health` y `/actuator/health`: Chequeo de estado del servicio y de Samba AD.

### Gestión de Privilegios Linux (Wheel / Samba AD & BIND)
- `GET /api/v1/privileges/status`: Devuelve el informe completo de auditoría de Samba AD, miembros de `wheel` y estado de cumplimiento.
- `GET /api/v1/privileges/verify?username={user}&provider={prov}`: Verifica puntualmente si un usuario tiene permiso `wheel` en Linux.
- `POST /api/v1/privileges/verify-bind`: Valida usuarios originarios del módulo `astral-ad-dns` mediante bind LDAP.
- `POST /api/v1/privileges/sync-wheel`: Sincroniza al usuario administrador de AD en el grupo `wheel` del host Linux.

---

## 🔒 Ejemplos de Auditoría y Sincronización vía cURL

```bash
# Verificar informe de auditoría
curl -s http://127.0.0.1:8181/api/v1/privileges/status | jq .

# Verificar permiso de un usuario específico
curl -s "http://127.0.0.1:8181/api/v1/privileges/verify?username=Administrator" | jq .

# Sincronizar administrador de AD en el grupo wheel de Linux
curl -s -X POST http://127.0.0.1:8181/api/v1/privileges/sync-wheel \
  -H "Content-Type: application/json" \
  -d '{"username":"Administrator","createLinuxUserIfMissing":true}' | jq .
```
