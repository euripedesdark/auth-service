# Deployment and Integration Guide — Auth Service

## 1. Purpose

The Auth Service is the central authentication and identity-resolution service for systems that need to validate users against Active Directory (AD/LDAP).

The main contract is **REST + HTTP Basic Authentication**, with additional support for short-lived **Bearer JWT** for modern integrations. The Auth Service does not implement OAuth2/OIDC as an authorization server.

Current flow:

~~~text
ERP / SGDB-adapter / Firewall / Proxy / other service
                    |
                    | HTTPS + HTTP Basic
                    v
             Auth Service :8181
                    |
                    | LDAPS
                    v
             Active Directory
~~~

The Auth Service returns the identity:

~~~json
{
  "identityId": "CN=Euripedes Batista,OU=Usuarios,DC=homelab,DC=local",
  "username": "euripedes",
  "provider": "AD",
  "groups": [
    "ERP-Administradores",
    "ERP-Financeiro"
  ]
}
~~~

> **Important:** AD groups are returned as identity data. The consumer service remains responsible for applying its own authorization rules.

---

## 2. Default deployment data

### HTTP port

The Spring Boot application uses **port 8181 by default**, as no different port is set in application.yml.

To change:

~~~bash
SERVER_PORT=8181
~~~

or:

~~~yaml
server:
  port: 8181
~~~

In production, place the Auth Service behind HTTPS/reverse proxy and do not expose port 8181 directly to the user network.

### Example address

Assuming:

- Auth Service: 192.168.2.50
- Port: 8181
- AD/DC: 100.100.100.100
- AD domain: homelab.local

The internal address will be:

~~~text
http://192.168.2.50:8181
~~~

In production:

~~~text
https://auth.homelab.local
~~~

The HTTPS hostname is only an example; it must be replaced with the real DNS of the installation.

---

## 3. Prerequisites

### Auth Service

- Java compatible with the build version.
- Maven for compilation.
- Network access to the AD.
- Trusted certificate for LDAPS.
- AD service account for queries.
- Working DNS.
- Synchronized server time.

### Active Directory

The Auth Service needs to:

1. establish the LDAPS connection;
2. authenticate the service account;
3. search users;
4. query the memberOf attribute;
5. authenticate the user-presented password.

The service account **must not be a domain administrative account**.

# 4. Auth Service configuration

Configuration currently uses environment variables.

## 4.1 Required variables

| Variable | Example | Purpose |
|---|---|---|
| AUTH_LDAP_URL | ldaps://100.100.100.100:636 | Secure endpoint |
| AUTH_LDAP_BASE | DC=homelab,DC=local | LDAP base |
| AUTH_LDAP_USER_DN | CN=svc-auth,OU=Service Accounts,DC=homelab,DC=local | Service account |
| AUTH_LDAP_PASSWORD | ******** | Service account password |
| AUTH_LDAP_USER_SEARCH_BASE | OU=Usuarios,DC=homelab,DC=local | User search base |

## 4.2 Optional variables

| Variable | Default | Purpose |
|---|---:|---|
| AUTH_LDAP_REQUIRE_SECURE | true | Requires ldaps:// |
| AUTH_LDAP_CONNECT_TIMEOUT_MS | 5000 | Connect timeout |
| AUTH_LDAP_READ_TIMEOUT_MS | 5000 | Read timeout |
| SERVER_PORT | 8181 | Application HTTP port |

### Example

~~~bash
export AUTH_LDAP_URL='ldaps://100.100.100.100:636'
export AUTH_LDAP_BASE='DC=homelab,DC=local'
export AUTH_LDAP_USER_DN='CN=svc-auth,OU=Service Accounts,DC=homelab,DC=local'
export AUTH_LDAP_PASSWORD='SERVICE_ACCOUNT_PASSWORD'
export AUTH_LDAP_USER_SEARCH_BASE='OU=Usuarios,DC=homelab,DC=local'
export AUTH_LDAP_REQUIRE_SECURE='true'
export AUTH_LDAP_CONNECT_TIMEOUT_MS='5000'
export AUTH_LDAP_READ_TIMEOUT_MS='5000'
export SERVER_PORT='8181'
~~~

**Do not put the service account password in Git.**

---

# 5. LDAPS and certificate

The current configuration requires LDAPS when:

~~~text
AUTH_LDAP_REQUIRE_SECURE=true
~~~

In that scenario, ldaps:// is mandatory.

Do not use ldap:// in production when the secure-LDAP requirement is enabled.

The certificate presented by the domain controller must be trusted by the JVM running the Auth Service.

Conceptual example:

~~~text
Auth Service
     |
     | TLS
     v
AD/DC:636
     |
     +-- AD server certificate
~~~

Before deployment, validate:

~~~bash
openssl s_client -connect 100.100.100.100:636 -showcerts
~~~

The test must run from the server where the Auth Service will execute.

# 6. Starting the Auth Service

After configuring the variables:

~~~bash
mvn clean package
~~~

Run:

~~~bash
java -jar target/auth-service-1.0.0-SNAPSHOT.jar
~~~

Or, during development:

~~~bash
mvn spring-boot:run
~~~

The application will be, by default, at:

~~~text
http://localhost:8181
~~~

---

# 7. Health check

The health endpoint is public:

~~~http
GET /actuator/health
~~~

Test:

~~~bash
curl -i http://localhost:8181/actuator/health
~~~

This endpoint does not replace the LDAP authentication test. It only verifies the application is responding.

---

# 8. User authentication

The main endpoint to authenticate a user is:

~~~http
POST /api/v1/identity/authenticate
Content-Type: application/json
~~~

Payload:

~~~json
{
  "username": "euripedes",
  "password": "USER_PASSWORD",
  "provider": "AD"
}
~~~

Example:

~~~bash
curl -i \
  -X POST \
  -H 'Content-Type: application/json' \
  -d '{\"username\":\"euripedes\",\"password\":\"USER_PASSWORD\",\"provider\":\"AD\"}' \
  https://auth.homelab.local/api/v1/identity/authenticate
~~~

Expected response:

~~~json
{
  "identityId": "CN=Euripedes,OU=Usuarios,DC=homelab,DC=local",
  "username": "euripedes",
  "provider": "AD",
  "groups": [
    "ERP-Administradores"
  ]
}
~~~

## 8.1 What the consumer must do with this response

The consumer system must:

1. identify the user;
2. get the groups;
3. locate its internal record, when one exists;
4. apply its own permissions;
5. create its own local session, if needed.

The Auth Service must not be treated as the ERP functional database.

# 9. Identity query using HTTP Basic

The GET identity endpoints require HTTP Basic authentication.

### Identity

~~~http
GET /api/v1/identity/me
Authorization: Basic <credentials>
~~~

Example:

~~~bash
curl -i \
  -u 'euripedes:USER_PASSWORD' \
  https://auth.homelab.local/api/v1/identity/me
~~~

### Groups

~~~bash
curl -i \
  -u 'euripedes:USER_PASSWORD' \
  https://auth.homelab.local/api/v1/identity/groups
~~~

### Provider

~~~bash
curl -i \
  -u 'euripedes:USER_PASSWORD' \
  https://auth.homelab.local/api/v1/identity/provider
~~~

### Authenticated state

~~~bash
curl -i \
  -u 'euripedes:USER_PASSWORD' \
  https://auth.homelab.local/api/v1/identity/authenticated
~~~

All four endpoints return the same Identity contract; the names exist to semantically separate consumer usages.

---

# 10. HTTP codes consumers must handle

| Code | Meaning |
|---:|---|
| 200 | Authentication/query completed |
| 400 | Invalid request |
| 401 | Missing or invalid credentials |
| 403 | Access denied |
| 503 | Identity provider unavailable |

### Important rule

The consumer system **must not treat 503 as a wrong password**.

~~~text
401 -> invalid credential / not authenticated
503 -> Auth Service or AD unavailable
~~~

This separates authentication failure from infrastructure outage.

---

# 11. ERP integration

The ERP can use the Auth Service as its central authentication point.

Recommended flow:

~~~text
User
  |
  v
ERP
  |
  | POST /api/v1/identity/authenticate
  | provider=AD
  v
Auth Service
  |
  | LDAPS
  v
AD
  |
  | identity + groups
  v
Auth Service
  |
  v
ERP
  |
  +--> local session
  +--> internal user
  +--> ERP permissions
~~~

## 11.1 Suggested ERP configuration

Example:

~~~properties
auth.service.url=https://auth.homelab.local
auth.service.authenticate-path=/api/v1/identity/authenticate
auth.service.me-path=/api/v1/identity/me
auth.service.groups-path=/api/v1/identity/groups
auth.provider=AD
~~~

If the ERP uses environment variables:

~~~bash
AUTH_SERVICE_URL=https://auth.homelab.local
AUTH_SERVICE_PROVIDER=AD
AUTH_SERVICE_AUTHENTICATE_PATH=/api/v1/identity/authenticate
AUTH_SERVICE_ME_PATH=/api/v1/identity/me
AUTH_SERVICE_GROUPS_PATH=/api/v1/identity/groups
~~~

## 11.2 Java/Spring example

The ERP must have a simple HTTP client:

~~~java
public IdentityDto authenticate(String username, String password) {
    return webClient.post()
        .uri(authServiceUrl + "/api/v1/identity/authenticate")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(new LoginRequest(username, password, "AD"))
        .retrieve()
        .bodyToMono(IdentityDto.class)
        .block();
}
~~~

DTO:

~~~java
public record IdentityDto(
    String identityId,
    String username,
    String provider,
    List<String> groups
) {}
~~~

---

# 12. How the ERP must handle AD groups

The Auth Service returns:

~~~json
"groups": [
  "ERP-Administradores",
  "ERP-Financeiro",
  "ERP-Vendas"
]
~~~

The ERP must map these groups to its internal permissions.

Example:

~~~text
AD group                    ERP permission

ERP-Administradores   ->    ADMIN
ERP-Financeiro        ->    FINANCEIRO
ERP-Vendas            ->    VENDAS
ERP-Compras           ->    COMPRAS
ERP-Estoque           ->    ESTOQUE
~~~

That mapping belongs to the ERP.

Do not put ERP-specific rules inside the Auth Service.

# 13. Database / RDBMS integration

## 13.1 Important point

A traditional RDBMS such as PostgreSQL or MySQL **must not be configured as if it could directly call a REST HTTP API on every login**, because the Auth Service is a REST service, not a native LDAP mechanism of the database.

Application architecture:

~~~text
Application
   |
   +----> Auth Service ----> AD
   |
   +----> PostgreSQL/MySQL
~~~

This is different from:

~~~text
PostgreSQL ----HTTP----> Auth Service
~~~

The second architecture is not a native integration provided by the current Auth Service.

---

# 14. ERP + PostgreSQL

For an ERP with PostgreSQL, the split is:

~~~text
                  +--> Auth Service --> AD
                  |
User --> ERP --+
                  |
                  +--> PostgreSQL
~~~

PostgreSQL stores:

- ERP data;
- internal users, if the ERP keeps that record;
- internal user/permission links;
- audit;
- functional settings.

The Auth Service is responsible for:

- validating AD credentials;
- resolving identity;
- returning AD groups.

## 14.1 Configuration example

~~~properties
auth.service.url=https://auth.homelab.local
database.url=jdbc:postgresql://db-erp:5432/brasilcloud
database.username=brasilcloud
database.password=********
~~~

Flow:

~~~text
1. User enters login/password
2. ERP calls Auth Service
3. Auth Service validates on AD
4. Auth Service returns identity
5. ERP finds the internal user by username/identityId
6. ERP applies permissions
7. ERP opens the session
~~~

---

# 15. Database systems that need to authenticate users

If the goal is to let a database administration tool, such as a SQL client, authenticate users with the same AD, there are two different possibilities.

### Option A — native RDBMS authentication

The RDBMS can be configured to use LDAP/AD directly, when the product supports it.

Flow:

~~~text
SQL Client --> RDBMS --> LDAP/AD
~~~

In that case the Auth Service does not take part in authentication.

### Option B — authentication through the Auth Service

To mandatorily go through the Auth Service:

~~~text
SQL Client
     |
     v
Authentication gateway/adapter/proxy
     |
     v
Auth Service
     |
     v
AD
~~~

This requires an intermediate component that knows the authentication protocol expected by the RDBMS and converts authentication to HTTP.

**The RDBMS must not be configured to simply point at http://auth-service:8181.**

---

# 16. Firewall/proxy integration

For a firewall, proxy or gateway with its own application:

~~~text
User
  |
  v
Firewall / Proxy
  |
  | HTTPS
  v
Auth Service
  |
  | LDAPS
  v
AD
~~~

Conceptual configuration:

~~~text
AUTH_SERVICE_URL=https://auth.homelab.local
AUTH_SERVICE_PROVIDER=AD
AUTH_SERVICE_AUTHENTICATE_PATH=/api/v1/identity/authenticate
~~~

The firewall/proxy receives:

~~~json
{
  "identityId": "...",
  "username": "euripedes",
  "provider": "AD",
  "groups": [
    "FW-Administradores"
  ]
}
~~~

And decides locally:

~~~text
FW-Administradores -> administration
FW-Operadores      -> operation
FW-Auditoria       -> read only
~~~

# 17. Integrating a new service

To add any new system:

### Step 1 — Define the URL

~~~text
https://auth.exemplo.local
~~~

### Step 2 — Define the authentication route

~~~text
POST /api/v1/identity/authenticate
~~~

### Step 3 — Send

~~~json
{
  "username": "usuario",
  "password": "senha",
  "provider": "AD"
}
~~~

### Step 4 — Validate the response

The service must require:

~~~text
HTTP 200
~~~

and validate:

~~~text
identityId
username
provider
groups
~~~

### Step 5 — Create the local identity

The system may look the user up by username or, when a persistent identifier is needed, by identityId.

### Step 6 — Apply local authorization

The consumer service decides which features the user may execute.

---

# 18. Network security

In production, do not expose the authentication endpoint without TLS.

Recommendation:

~~~text
                    INTERNET
                       |
                       X
                       |
                 [Firewall]
                       |
                       v
             Reverse Proxy / LB
                       |
                     HTTPS
                       |
                       v
                Auth Service
                    :8181
                       |
                     LDAPS
                    :636
                       |
                       v
                      AD
~~~

Port 8181 may stay restricted to the internal network or the reverse proxy.

Example:

~~~text
Reverse Proxy -> Auth Service:8181    ALLOW
ERP            -> Auth Service:8181    ALLOW
Firewall       -> Auth Service:8181    ALLOW
Users          -> Auth Service:8181    DENY
Internet       -> Auth Service:8181    DENY
~~~

The real policy must be adapted to the installation topology.

---

# 19. Recommended DNS

Create a stable name for the service:

~~~text
auth.homelab.local
~~~

Example:

~~~text
auth.homelab.local -> 192.168.2.50
~~~

Systems must use the hostname, not the IP directly:

~~~text
https://auth.homelab.local
~~~

This allows swapping server, balancer or IP without changing every consumer system.

---

# 20. Reverse proxy

Conceptual example with Nginx:

~~~nginx
server {
    listen 443 ssl;
    server_name auth.homelab.local;

    location / {
        proxy_pass http://127.0.0.1:8181;
        proxy_set_header Host $host;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto https;
    }
}
~~~

The certificate must be issued for the hostname used by consumers.

---

# 21. Timeouts and unavailability

Consumers must have their own timeout for Auth Service calls.

Do not let a login request stay blocked forever.

Example:

~~~text
Connect timeout: 5s
Read timeout:    5s
~~~

When the Auth Service returns:

~~~text
503 Service Unavailable
~~~

treat it as service/provider outage, not as a wrong password.

# 22. Do not store the AD password

ERP, firewall, proxy and other consumers must not store the user password for reuse.

Correct flow:

~~~text
password
  |
  v
Auth Service
  |
  v
AD
~~~

After authentication, the consumer must keep only the session/identity needed for its own operation.

---

# 23. Logs

Never log:

~~~text
password
Authorization: Basic ...
LDAP credentials
tokens containing credentials
~~~

You may log, per the audit policy:

~~~text
timestamp
consumer service
username
result
provider
technical reason without password
correlation/request id
~~~

Example:

~~~text
2026-09-30T15:30:00Z
service=erp
username=euripedes
provider=AD
result=SUCCESS
~~~

---

# 24. Swagger / OpenAPI

The application exposes OpenAPI documentation at:

~~~text
/v3/api-docs
~~~

and Swagger UI at:

~~~text
/swagger-ui.html
~~~

Example:

~~~text
https://auth.homelab.local/swagger-ui.html
~~~

These endpoints help during integration and approval.

In production, evaluate public exposure of the documentation and restrict it per the security policy.

---

# 25. Deployment checklist

## Auth Service

- [ ] Java installed
- [ ] Maven working
- [ ] mvn clean test executed
- [ ] mvn clean package executed
- [ ] JAR built
- [ ] SERVER_PORT set when needed
- [ ] LDAP variables configured
- [ ] LDAP password out of Git
- [ ] LDAPS working
- [ ] trusted AD certificate
- [ ] working DNS
- [ ] synchronized clock
- [ ] firewall allowing only required consumers

## Active Directory

- [ ] service account created
- [ ] service account without unneeded admin privileges
- [ ] LDAPS working
- [ ] users found by the configured base
- [ ] sAMAccountName working
- [ ] memberOf attribute available

## ERP

- [ ] Auth Service URL configured
- [ ] AD provider
- [ ] authentication endpoint configured
- [ ] identity DTO implemented
- [ ] 401 handling
- [ ] 403 handling
- [ ] 503 handling
- [ ] AD group mapping
- [ ] local permissions configured
- [ ] password not stored

## Database / RDBMS

- [ ] separate application authentication from database access
- [ ] do not point the RDBMS at an Auth Service HTTP URL
- [ ] decide whether authentication is native LDAP/AD or via adapter/proxy
- [ ] keep database credentials apart from user credentials
- [ ] restrict network access to the database

## Firewall / Proxy

- [ ] Auth Service URL configured
- [ ] HTTPS enabled
- [ ] AD provider
- [ ] mapped groups
- [ ] 401 treated as authentication failure
- [ ] 503 treated as outage
- [ ] no password stored

# 26. Full approval test

## Test 1 — Service

~~~bash
curl -i https://auth.homelab.local/actuator/health
~~~

Expected result:

~~~text
HTTP/1.1 200
~~~

## Test 2 — Valid credential

~~~bash
curl -i \
  -X POST \
  -H 'Content-Type: application/json' \
  -d '{\"username\":\"usuario-teste\",\"password\":\"SENHA\",\"provider\":\"AD\"}' \
  https://auth.homelab.local/api/v1/identity/authenticate
~~~

Expected result:

~~~text
HTTP 200
~~~

## Test 3 — Invalid credential

Use a wrong password.

Expected result:

~~~text
HTTP 401
~~~

## Test 4 — AD unavailable

Temporarily block the Auth Service access to the domain controller.

Expected result:

~~~text
HTTP 503
~~~

## Test 5 — AD group

Use a user belonging to known groups.

Confirm that:

~~~json
"groups": [...]
~~~

contains the expected groups.

---

# 27. Architecture model for the three systems

For ERP, RDBMS/database services and firewall/proxy:

~~~text
                         +----------------+
                         |      AD/LDAP   |
                         +-------^--------+
                                 |
                               LDAPS
                                 |
                         +-------+--------+
                         |   AUTH SERVICE |
                         |    HTTPS       |
                         |     :8181      |
                         +---^-------^----+
                             |       |
                    HTTPS    |       | HTTPS
                             |       |
                +------------+       +------------+
                |                                 |
        +-------+-------+                 +-------+-------+
        |      ERP      |                 | Firewall/Proxy |
        +-------+-------+                 +---------------+
                |
                |
             JDBC
                |
        +-------v-------+
        | PostgreSQL /  |
        | other RDBMS   |
        +---------------+
~~~

The central point is:

**The Auth Service authenticates identity; each product remains responsible for its data and functional authorization.**

---

# 28. Current contract limitations

The consumer must take the current contract exactly as implemented.

Currently existing:

~~~text
POST /api/v1/identity/authenticate
GET  /api/v1/identity/me
GET  /api/v1/identity/groups
GET  /api/v1/identity/provider
GET  /api/v1/identity/authenticated
GET  /actuator/health
~~~

HTTP authentication is based on Basic Authentication.

The current contract **must not be documented as if it were already OAuth2/OIDC/JWT**.

If a token protocol is implemented in the future, that will be a new contract and consumers must be migrated in a controlled way.

---

# 29. Summary for development teams

To integrate a new system:

~~~text
1. Configure AUTH_SERVICE_URL
2. Use HTTPS
3. Call POST /api/v1/identity/authenticate
4. Send username/password/provider=AD
5. Validate HTTP 200
6. Read identityId, username, provider and groups
7. Create/recover the local identity
8. Apply local authorization
9. Treat 401 as credential failure
10. Treat 503 as outage
11. Never store the AD password
12. Do not couple the system to the internal AD structure
~~~

This is the integration standard for ERP, firewall/proxy and other services needing the Auth Service as central authentication point.

---

# 30. Bearer Token authentication

New integrations should prefer the endpoint:

`http
POST /api/v1/identity/token
Content-Type: application/json
`

Payload:

`json
{
  "username": "usuario",
  "password": "senha",
  "provider": "AD"
}
`

Response:

`json
{
  "accessToken": "<JWT>",
  "tokenType": "Bearer",
  "expiresIn": 900,
  "identity": {
    "identityId": "CN=Usuario,OU=Usuarios,DC=homelab,DC=local",
    "username": "usuario",
    "provider": "AD",
    "groups": ["ERP-Vendas"]
  }
}
`

The token is signed by the Auth Service, carries identity and groups, and is short-lived. The consumer does not need to resend the AD password on every call.

### Mandatory configuration

Set a strong key and keep it out of Git:

`bash
export AUTH_TOKEN_SECRET='a-random-key-with-at-least-32-bytes'
export AUTH_TOKEN_ISSUER='auth-service'
export AUTH_TOKEN_TTL_SECONDS='900'
`

Generate a proper key, for example:

`bash
openssl rand -base64 48
`

### Using the token

After obtaining the token:

`bash
curl -i \
  -H "Authorization: Bearer <JWT>" \
  https://auth.homelab.local/api/v1/identity/me
`

The `/me`, `/groups`, `/provider` and `/authenticated` endpoints accept Bearer Token. HTTP Basic remains available for existing integrations.

### Security

- Do not store the AD password in the ERP.
- Do not log the JWT or the Authorization header.
- Use HTTPS between consumers and the Auth Service.
- Keep the TTL short.
- The same key used to validate the token must be protected as infrastructure secret.
- For multiple Auth Service instances, all must use the same key and issuer.
- The token represents identity/authorization at issuance time; later AD group changes take effect when a new token is issued.

### Compatibility

`POST /api/v1/identity/authenticate` keeps returning only `IdentityDto`, preserving existing consumers.

For new integrations, use `POST /api/v1/identity/token`.
