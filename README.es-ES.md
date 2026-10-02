# BrasilCloud Auth Service

**Servicio central de autenticación e identidad** — valida usuarios contra Active Directory (AD/LDAP) y devuelve identidad + grupos a ERP, firewall, proxy y otros servicios. Contrato **REST + HTTP Basic**, con **Bearer JWT de corta duración** para integraciones modernas. No es servidor OAuth2/OIDC.

## Acerca de

Punto único de autenticación del ecosistema BrasilCloud (usado por Brasil SaaS ERP). Autentica en el AD por LDAPS. Los grupos del AD son datos de identidad: **la autorización pertenece a cada sistema consumidor**.

## Stack

| Capa | Tecnología |
|---|---|
| Lenguaje | Java 21 |
| Framework | Spring Boot 3.3.5 (Web, Security, Actuator) |
| Directorio | Spring LDAP |
| Tokens | JWT corto (oauth2-jose) |
| Docs API | springdoc-openapi (Swagger UI) |
| Build | Maven |
| Puerto | `8181` |

## Ejecutar

```bash
export AUTH_LDAP_URL='ldaps://100.100.100.100:636'
export AUTH_LDAP_BASE='DC=homelab,DC=local'
export AUTH_LDAP_USER_DN='CN=svc-auth,OU=Service Accounts,DC=homelab,DC=local'
export AUTH_LDAP_PASSWORD='CLAVE_CUENTA_SERVICIO'
export AUTH_LDAP_USER_SEARCH_BASE='OU=Usuarios,DC=homelab,DC=local'

mvn clean package
java -jar target/auth-service-1.0.0-SNAPSHOT.jar
```

Desarrollo: `mvn spring-boot:run`. Puerto: `SERVER_PORT=8181`. En producción detrás de HTTPS/reverse proxy.

## Configuración

| Variable | Defecto | Finalidad |
|---|---|---|
| `AUTH_LDAP_URL` | — | Endpoint LDAP seguro |
| `AUTH_LDAP_BASE` | — | Base LDAP |
| `AUTH_LDAP_USER_DN` | — | Cuenta de servicio (no-admin) |
| `AUTH_LDAP_PASSWORD` | — | Clave (**fuera de Git**) |
| `AUTH_LDAP_USER_SEARCH_BASE` | — | Base de usuarios |
| `AUTH_LDAP_REQUIRE_SECURE` | `true` | Exige `ldaps://` |
| `AUTH_TOKEN_SECRET` | — | Clave JWT (32+ bytes, fuera de Git) |
| `AUTH_TOKEN_TTL_SECONDS` | `900` | Vida del JWT |
| `SERVER_PORT` | `8181` | Puerto HTTP |

## Endpoints

| Método | Ruta | Uso |
|---|---|---|
| `POST` | `/api/v1/identity/authenticate` | Login |
| `POST` | `/api/v1/identity/token` | Login con Bearer JWT |
| `GET` | `/api/v1/identity/me` | Identidad |
| `GET` | `/api/v1/identity/groups` | Grupos |
| `GET` | `/actuator/health` | Salud (público) |
| `GET` | `/swagger-ui.html` | Docs interactiva |

`401` = credencial inválida, `503` = AD/servicio no disponible (nunca trate 503 como clave errada). No registre claves AD ni JWT en logs.

## Guías de Implantación e Integración

| Idioma | Guía |
|---|---|
| PT-BR | docs/GUIA-INTEGRACAO-AUTH-SERVICE.pt-BR.md |
| EN-US | docs/GUIA-INTEGRACAO-AUTH-SERVICE.en-US.md |
| ES-ES | docs/GUIA-INTEGRACAO-AUTH-SERVICE.es-ES.md |
| FR-FR | docs/GUIA-INTEGRACAO-AUTH-SERVICE.fr-FR.md |

## Requisitos: LDAP / LDAPS / Active Directory

- **Linux:** Samba Active Directory — guía completa:
  https://medium.com/meetcyber/full-guide-deploying-samba-active-directory-on-fedora-and-rocky-with-hybrid-dns-and-gpo-support-30f47fab1e90
- **Windows:** Windows Server Active Directory con LDAPS habilitado.

Puede evaluarse sin el entorno corporativo completo; en producción úselo integrado a un directorio LDAP/LDAPS, con cuenta de servicio sin privilegios.

## Apoya el Proyecto

BrasilCloud Auth Service es open source mantenido por un solo desarrollador. Si le ayudó a usted, su empresa o su equipo, considere apoyarlo.

**PIX (solo Brasil):**

```
24adc62c-b073-4587-974d-03fe35f6733f
```

Cualquier valor paga servidores y certificados. No compra SLA en ningún issue.

### Transferencia internacional

PIX no funciona fuera de Brasil. **Desde un banco de EE. UU.**, transferencia doméstica. **Desde otro lugar**, Swift internacional.

| | |
|---|---|
| **Nombre** | Euripedes Batista de Paiva Junior |
| **Tipo de cuenta** | Checking |
| **Routing number** (wire y ACH) | `101019628` |
| **Número de cuenta** | `215822927677` |
| **Banco** | Wise US Inc, 108 W 13th St, Wilmington, DE, 19801, United States |
| **SWIFT/BIC** | `TRWIUS35XXX` |

## Soporte

- Email: euripedesdark@gmail.com
- GitHub Issues: https://github.com/euripedesdark/auth-service/issues
- Docs: `docs/`

## Cómo Contribuir

1. Abra un issue antes de escribir código.
2. Cree una rama descriptiva.
3. Commit por tema y ejecute pruebas (`mvn test`).
4. Abra el Pull Request contra `main`.

## Licencia

Este proyecto está bajo **GNU AGPL v3**. Ver `LICENSE.es-ES.md` (nota en español; texto oficial en inglés en `LICENSE.md`).

(c) Euripedes Batista de Paiva Junior
