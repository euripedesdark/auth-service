# Guía de Implantación e Integración — Auth Service

## 1. Objetivo

El Auth Service es el servicio central de autenticación y resolución de identidad para los sistemas que necesitan validar usuarios contra Active Directory (AD/LDAP).

El contrato principal es **REST + HTTP Basic Authentication**, con soporte adicional de **Bearer JWT de corta duración** para integraciones modernas. El Auth Service no implementa OAuth2/OIDC como servidor de autorización.

Flujo actual:

~~~text
ERP / SGDB-adapter / Firewall / Proxy / otro servicio
                    |
                    | HTTPS + HTTP Basic
                    v
             Auth Service :8181
                    |
                    | LDAPS
                    v
             Active Directory
~~~

El Auth Service devuelve la identidad:

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

> **Importante:** los grupos del AD se devuelven como datos de identidad. El servicio consumidor sigue siendo responsable de aplicar sus propias reglas de autorización.

---

## 2. Datos estándar de implantación

### Puerto HTTP

La aplicación Spring Boot utiliza el **puerto 8181 por defecto**, pues no hay otro puerto definido en application.yml.

Para cambiar:

~~~bash
SERVER_PORT=8181
~~~

o:

~~~yaml
server:
  port: 8181
~~~

En producción, coloque el Auth Service detrás de HTTPS/reverse proxy y no exponga el puerto 8181 directamente a la red de usuarios.

### Dirección de ejemplo

Suponiendo:

- Auth Service: 192.168.2.50
- Puerto: 8181
- AD/DC: 100.100.100.100
- dominio AD: homelab.local

La dirección interna será:

~~~text
http://192.168.2.50:8181
~~~

En producción:

~~~text
https://auth.homelab.local
~~~

El hostname HTTPS es solo un ejemplo; debe sustituirse por el DNS real de la instalación.

---

## 3. Prerrequisitos

### Auth Service

- Java compatible con la versión del build.
- Maven para compilar.
- Acceso de red al AD.
- Certificado confiable para LDAPS.
- Cuenta de servicio del AD para consultas.
- DNS funcional.
- Hora sincronizada en los servidores.

### Active Directory

El Auth Service necesita:

1. establecer la conexión LDAPS;
2. autenticar la cuenta de servicio;
3. buscar usuarios;
4. consultar el atributo memberOf;
5. autenticar la contraseña presentada por el usuario.

La cuenta de servicio **no debe ser una cuenta administrativa de dominio**.

# 4. Configuración del Auth Service

La configuración actual usa variables de entorno.

## 4.1 Variables obligatorias

| Variable | Ejemplo | Finalidad |
|---|---|---|
| AUTH_LDAP_URL | ldaps://100.100.100.100:636 | Endpoint seguro |
| AUTH_LDAP_BASE | DC=homelab,DC=local | Base LDAP |
| AUTH_LDAP_USER_DN | CN=svc-auth,OU=Service Accounts,DC=homelab,DC=local | Cuenta de servicio |
| AUTH_LDAP_PASSWORD | ******** | Clave de la cuenta |
| AUTH_LDAP_USER_SEARCH_BASE | OU=Usuarios,DC=homelab,DC=local | Base de usuarios |

## 4.2 Variables opcionales

| Variable | Defecto | Finalidad |
|---|---:|---|
| AUTH_LDAP_REQUIRE_SECURE | true | Exige ldaps:// |
| AUTH_LDAP_CONNECT_TIMEOUT_MS | 5000 | Timeout de conexión |
| AUTH_LDAP_READ_TIMEOUT_MS | 5000 | Timeout de lectura |
| SERVER_PORT | 8181 | Puerto HTTP |

### Ejemplo

~~~bash
export AUTH_LDAP_URL='ldaps://100.100.100.100:636'
export AUTH_LDAP_BASE='DC=homelab,DC=local'
export AUTH_LDAP_USER_DN='CN=svc-auth,OU=Service Accounts,DC=homelab,DC=local'
export AUTH_LDAP_PASSWORD='CLAVE_CUENTA_SERVICIO'
export AUTH_LDAP_USER_SEARCH_BASE='OU=Usuarios,DC=homelab,DC=local'
export AUTH_LDAP_REQUIRE_SECURE='true'
export AUTH_LDAP_CONNECT_TIMEOUT_MS='5000'
export AUTH_LDAP_READ_TIMEOUT_MS='5000'
export SERVER_PORT='8181'
~~~

**No ponga la clave de la cuenta de servicio en Git.**

---

# 5. LDAPS y certificado

La configuración actual exige LDAPS cuando:

~~~text
AUTH_LDAP_REQUIRE_SECURE=true
~~~

En ese escenario, ldaps:// es obligatorio.

No utilice ldap:// en producción cuando la exigencia de LDAP seguro esté habilitada.

El certificado presentado por el controlador de dominio debe ser confiable para la JVM que ejecuta el Auth Service.

Ejemplo conceptual:

~~~text
Auth Service
     |
     | TLS
     v
AD/DC:636
     |
     +-- certificado del servidor AD
~~~

Antes de la implantación, valide:

~~~bash
openssl s_client -connect 100.100.100.100:636 -showcerts
~~~

La prueba debe hacerse desde el servidor donde se ejecutará el Auth Service.

# 6. Levantando el Auth Service

Después de configurar las variables:

~~~bash
mvn clean package
~~~

Ejecute:

~~~bash
java -jar target/auth-service-1.0.0-SNAPSHOT.jar
~~~

O, durante el desarrollo:

~~~bash
mvn spring-boot:run
~~~

La aplicación quedará, por defecto, en:

~~~text
http://localhost:8181
~~~

---

# 7. Verificación de salud

El endpoint de health es público:

~~~http
GET /actuator/health
~~~

Prueba:

~~~bash
curl -i http://localhost:8181/actuator/health
~~~

Este endpoint no sustituye la prueba de autenticación LDAP. Solo verifica que la aplicación responde.

---

# 8. Autenticación de usuario

El endpoint principal para autenticar un usuario es:

~~~http
POST /api/v1/identity/authenticate
Content-Type: application/json
~~~

Payload:

~~~json
{
  "username": "euripedes",
  "password": "CLAVE_USUARIO",
  "provider": "AD"
}
~~~

Ejemplo:

~~~bash
curl -i \
  -X POST \
  -H 'Content-Type: application/json' \
  -d '{\"username\":\"euripedes\",\"password\":\"CLAVE_USUARIO\",\"provider\":\"AD\"}' \
  https://auth.homelab.local/api/v1/identity/authenticate
~~~

Respuesta esperada:

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

## 8.1 Qué debe hacer el consumidor con esta respuesta

El sistema consumidor debe:

1. identificar al usuario;
2. obtener los grupos;
3. localizar su registro interno, cuando exista;
4. aplicar sus propios permisos;
5. crear su propia sesión local, si es necesario.

El Auth Service no debe tratarse como base de datos funcional del ERP.

# 9. Consulta de identidad con HTTP Basic

Los endpoints GET de identidad exigen autenticación HTTP Basic.

### Identidad

~~~http
GET /api/v1/identity/me
Authorization: Basic <credenciales>
~~~

Ejemplo:

~~~bash
curl -i \
  -u 'euripedes:CLAVE_USUARIO' \
  https://auth.homelab.local/api/v1/identity/me
~~~

### Grupos

~~~bash
curl -i \
  -u 'euripedes:CLAVE_USUARIO' \
  https://auth.homelab.local/api/v1/identity/groups
~~~

### Provider

~~~bash
curl -i \
  -u 'euripedes:CLAVE_USUARIO' \
  https://auth.homelab.local/api/v1/identity/provider
~~~

### Estado autenticado

~~~bash
curl -i \
  -u 'euripedes:CLAVE_USUARIO' \
  https://auth.homelab.local/api/v1/identity/authenticated
~~~

Los cuatro endpoints devuelven el mismo contrato Identity; los nombres existen para separar semánticamente los usos.

---

# 10. Códigos HTTP que los consumidores deben tratar

| Código | Significado |
|---:|---|
| 200 | Autenticación/consulta concluida |
| 400 | Petición inválida |
| 401 | Credenciales ausentes o inválidas |
| 403 | Acceso denegado |
| 503 | Proveedor de identidad no disponible |

### Regla importante

El consumidor **no debe tratar 503 como clave errada**.

~~~text
401 -> credencial inválida / no autenticado
503 -> Auth Service o AD no disponible
~~~

Esto diferencia falla de autenticación de indisponibilidad.

---

# 11. Integración del ERP

El ERP puede usar el Auth Service como punto central de autenticación.

Flujo recomendado:

~~~text
Usuario
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
  | identidad + grupos
  v
Auth Service
  |
  v
ERP
  |
  +--> sesión local
  +--> usuario interno
  +--> permisos del ERP
~~~

## 11.1 Configuración sugerida del ERP

Ejemplo:

~~~properties
auth.service.url=https://auth.homelab.local
auth.service.authenticate-path=/api/v1/identity/authenticate
auth.service.me-path=/api/v1/identity/me
auth.service.groups-path=/api/v1/identity/groups
auth.provider=AD
~~~

Si el ERP usa variables de entorno:

~~~bash
AUTH_SERVICE_URL=https://auth.homelab.local
AUTH_SERVICE_PROVIDER=AD
AUTH_SERVICE_AUTHENTICATE_PATH=/api/v1/identity/authenticate
AUTH_SERVICE_ME_PATH=/api/v1/identity/me
AUTH_SERVICE_GROUPS_PATH=/api/v1/identity/groups
~~~

## 11.2 Ejemplo Java/Spring

El ERP debe tener un cliente HTTP simple:

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

# 12. Cómo debe tratar el ERP los grupos del AD

El Auth Service devuelve:

~~~json
"groups": [
  "ERP-Administradores",
  "ERP-Financeiro",
  "ERP-Vendas"
]
~~~

El ERP debe mapear estos grupos a sus permisos internos.

Ejemplo:

~~~text
AD group                    ERP permission

ERP-Administradores   ->    ADMIN
ERP-Financeiro        ->    FINANCEIRO
ERP-Vendas            ->    VENDAS
ERP-Compras           ->    COMPRAS
ERP-Estoque           ->    ESTOQUE
~~~

Ese mapeo pertenece al ERP.

No ponga reglas específicas del ERP dentro del Auth Service.

# 13. Integración con base de datos / SGDB

## 13.1 Punto importante

Un SGDB tradicional, como PostgreSQL o MySQL, **no debe configurarse como si pudiera llamar directamente a una API REST HTTP en cada login**, porque el Auth Service es un servicio REST y no un mecanismo LDAP nativo del banco.

Arquitectura de la aplicación:

~~~text
Aplicación
   |
   +----> Auth Service ----> AD
   |
   +----> PostgreSQL/MySQL
~~~

Esto es diferente de:

~~~text
PostgreSQL ----HTTP----> Auth Service
~~~

La segunda arquitectura no es una integración nativa del Auth Service actual.

---

# 14. ERP + PostgreSQL

Para un ERP con PostgreSQL, la separación es:

~~~text
                  +--> Auth Service --> AD
                  |
Usuario --> ERP --+
                  |
                  +--> PostgreSQL
~~~

PostgreSQL guarda:

- datos del ERP;
- usuarios internos, si el ERP mantiene ese registro;
- vínculos internos entre usuario y permisos;
- auditoría;
- configuraciones funcionales.

El Auth Service es responsable de:

- validar credenciales AD;
- resolver identidad;
- devolver grupos AD.

## 14.1 Ejemplo de configuración

~~~properties
auth.service.url=https://auth.homelab.local
database.url=jdbc:postgresql://db-erp:5432/brasilcloud
database.username=brasilcloud
database.password=********
~~~

Flujo:

~~~text
1. El usuario informa login/clave
2. El ERP llama al Auth Service
3. El Auth Service valida en el AD
4. El Auth Service devuelve identidad
5. El ERP busca al usuario interno por username/identityId
6. El ERP aplica permisos
7. El ERP abre la sesión
~~~

---

# 15. Sistemas de banco que necesitan autenticar usuarios

Si el objetivo es permitir que una herramienta de administración de banco, como un cliente SQL, autentique usuarios con el mismo AD, hay dos posibilidades.

### Opción A — autenticación nativa del SGDB

El SGDB puede configurarse para usar LDAP/AD directamente, cuando el producto lo soporte.

Flujo:

~~~text
Cliente SQL --> SGDB --> LDAP/AD
~~~

En ese caso el Auth Service no participa.

### Opción B — autenticación vía Auth Service

Para pasar obligatoriamente por el Auth Service:

~~~text
Cliente SQL
     |
     v
Gateway/adapter/proxy de autenticación
     |
     v
Auth Service
     |
     v
AD
~~~

Esto exige un componente intermedio que conozca el protocolo esperado por el SGDB y convierta la autenticación a HTTP.

**No se debe configurar el SGDB para apuntar simplemente a http://auth-service:8181.**

---

# 16. Integración con firewall/proxy

Para firewall, proxy o gateway con aplicación propia:

~~~text
Usuario
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

Configuración conceptual:

~~~text
AUTH_SERVICE_URL=https://auth.homelab.local
AUTH_SERVICE_PROVIDER=AD
AUTH_SERVICE_AUTHENTICATE_PATH=/api/v1/identity/authenticate
~~~

El firewall/proxy recibe:

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

Y decide localmente:

~~~text
FW-Administradores -> administración
FW-Operadores      -> operación
FW-Auditoría       -> solo lectura
~~~

# 17. Integración de un nuevo servicio

Para agregar cualquier sistema nuevo:

### Paso 1 — Definir la URL

~~~text
https://auth.exemplo.local
~~~

### Paso 2 — Definir la ruta de autenticación

~~~text
POST /api/v1/identity/authenticate
~~~

### Paso 3 — Enviar

~~~json
{
  "username": "usuario",
  "password": "senha",
  "provider": "AD"
}
~~~

### Paso 4 — Validar la respuesta

El servicio debe exigir:

~~~text
HTTP 200
~~~

y validar:

~~~text
identityId
username
provider
groups
~~~

### Paso 5 — Crear la identidad local

El sistema puede buscar al usuario por username o, cuando necesite un identificador persistente, por identityId.

### Paso 6 — Aplicar autorización local

El servicio consumidor decide qué funcionalidades puede ejecutar el usuario.

---

# 18. Seguridad de red

En producción, no exponga el endpoint de autenticación sin TLS.

Recomendación:

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

El puerto 8181 puede quedar restringido a la red interna o al reverse proxy.

Ejemplo:

~~~text
Reverse Proxy -> Auth Service:8181    ALLOW
ERP            -> Auth Service:8181    ALLOW
Firewall       -> Auth Service:8181    ALLOW
Usuarios       -> Auth Service:8181    DENY
Internet       -> Auth Service:8181    DENY
~~~

La política real debe adaptarse a la topología.

---

# 19. DNS recomendado

Cree un nombre estable para el servicio:

~~~text
auth.homelab.local
~~~

Ejemplo:

~~~text
auth.homelab.local -> 192.168.2.50
~~~

Los sistemas deben usar el hostname, no el IP directamente:

~~~text
https://auth.homelab.local
~~~

Esto permite cambiar servidor, balanceador o IP sin tocar todos los consumidores.

---

# 20. Reverse proxy

Ejemplo conceptual con Nginx:

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

El certificado debe emitirse para el hostname de los consumidores.

---

# 21. Timeouts e indisponibilidad

Los consumidores deben tener timeout propio para llamadas al Auth Service.

No deje una petición de login bloqueada indefinidamente.

Ejemplo:

~~~text
Connect timeout: 5s
Read timeout:    5s
~~~

Cuando el Auth Service devuelva:

~~~text
503 Service Unavailable
~~~

trátelo como indisponibilidad, no como clave incorrecta.

# 22. No guardar la clave AD

ERP, firewall, proxy y otros consumidores no deben guardar la clave del usuario para reutilizarla.

Flujo correcto:

~~~text
clave
  |
  v
Auth Service
  |
  v
AD
~~~

Tras la autenticación, el consumidor debe mantener solo la sesión/identidad necesaria.

---

# 23. Logs

Nunca registre en log:

~~~text
password
Authorization: Basic ...
credenciales LDAP
tokens con credenciales
~~~

Puede registrar, según auditoría:

~~~text
timestamp
servicio consumidor
username
resultado
provider
motivo técnico sin clave
correlation/request id
~~~

Ejemplo:

~~~text
2026-09-30T15:30:00Z
service=erp
username=euripedes
provider=AD
result=SUCCESS
~~~

---

# 24. Swagger / OpenAPI

La aplicación expone documentación OpenAPI en:

~~~text
/v3/api-docs
~~~

y Swagger UI en:

~~~text
/swagger-ui.html
~~~

Ejemplo:

~~~text
https://auth.homelab.local/swagger-ui.html
~~~

Útiles en integración y homologación.

En producción, evalúe exponer la documentación y restrinja según la política.

---

# 25. Checklist de implantación

## Auth Service

- [ ] Java instalado
- [ ] Maven funcionando
- [ ] mvn clean test ejecutado
- [ ] mvn clean package ejecutado
- [ ] JAR generado
- [ ] SERVER_PORT definido si hace falta
- [ ] variables LDAP configuradas
- [ ] clave LDAP fuera de Git
- [ ] LDAPS funcionando
- [ ] certificado AD confiable
- [ ] DNS funcionando
- [ ] reloj sincronizado
- [ ] firewall liberando solo consumidores necesarios

## Active Directory

- [ ] cuenta de servicio creada
- [ ] cuenta sin privilegios innecesarios
- [ ] LDAPS funcionando
- [ ] usuarios ubicados por la base configurada
- [ ] sAMAccountName funcionando
- [ ] atributo memberOf disponible

## ERP

- [ ] URL del Auth Service configurada
- [ ] provider AD
- [ ] endpoint de autenticación configurado
- [ ] DTO de identidad implementado
- [ ] tratamiento de 401
- [ ] tratamiento de 403
- [ ] tratamiento de 503
- [ ] mapeo de grupos AD
- [ ] permisos locales configurados
- [ ] clave no guardada

## Banco / SGDB

- [ ] separar autenticación de la app del acceso al banco
- [ ] no apuntar el SGDB a una URL HTTP del Auth Service
- [ ] definir si será LDAP/AD nativo o vía adapter/proxy
- [ ] credenciales del banco separadas de las de usuario
- [ ] restringir red hacia el banco

## Firewall / Proxy

- [ ] URL del Auth Service configurada
- [ ] HTTPS habilitado
- [ ] provider AD
- [ ] grupos mapeados
- [ ] 401 como falla de autenticación
- [ ] 503 como indisponibilidad
- [ ] ninguna clave guardada

# 26. Prueba completa de homologación

## Prueba 1 — Servicio

~~~bash
curl -i https://auth.homelab.local/actuator/health
~~~

Resultado esperado:

~~~text
HTTP/1.1 200
~~~

## Prueba 2 — Credencial válida

~~~bash
curl -i \
  -X POST \
  -H 'Content-Type: application/json' \
  -d '{\"username\":\"usuario-teste\",\"password\":\"CLAVE\",\"provider\":\"AD\"}' \
  https://auth.homelab.local/api/v1/identity/authenticate
~~~

Resultado esperado:

~~~text
HTTP 200
~~~

## Prueba 3 — Credencial inválida

Use una clave incorrecta.

Resultado esperado:

~~~text
HTTP 401
~~~

## Prueba 4 — AD no disponible

Bloquee temporalmente el acceso del Auth Service al controlador de dominio.

Resultado esperado:

~~~text
HTTP 503
~~~

## Prueba 5 — Grupo AD

Use un usuario de grupos conocidos.

Confirme que:

~~~json
"groups": [...]
~~~

contiene los grupos esperados.

---

# 27. Modelo de arquitectura para los tres sistemas

Para ERP, SGDB/servicios de banco y firewall/proxy:

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
        | otro SGDB     |
        +---------------+
~~~

El punto central es:

**El Auth Service autentica la identidad; cada producto sigue responsable de sus datos y su autorización funcional.**

---

# 28. Limitaciones del contrato actual

El consumidor debe tomar el contrato actual tal como está implementado.

Actualmente existen:

~~~text
POST /api/v1/identity/authenticate
GET  /api/v1/identity/me
GET  /api/v1/identity/groups
GET  /api/v1/identity/provider
GET  /api/v1/identity/authenticated
GET  /actuator/health
~~~

La autenticación HTTP se basa en Basic Authentication.

El contrato actual **no debe documentarse como si ya fuera OAuth2/OIDC/JWT**.

Si en el futuro se implementa un protocolo de token, será un nuevo contrato y los consumidores deberán migrarse de forma controlada.

---

# 29. Resumen para equipos de desarrollo

Para integrar un sistema nuevo:

~~~text
1. Configure AUTH_SERVICE_URL
2. Use HTTPS
3. Llame POST /api/v1/identity/authenticate
4. Envíe username/password/provider=AD
5. Valide HTTP 200
6. Lea identityId, username, provider y groups
7. Cree/recupere la identidad local
8. Aplique autorización local
9. Trate 401 como falla de credencial
10. Trate 503 como indisponibilidad
11. Nunca guarde la clave AD
12. No acople el sistema a la estructura interna del AD
~~~

Este es el estándar de integración para ERP, firewall/proxy y demás servicios que necesiten el Auth Service como punto central.

---

# 30. Autenticación por Bearer Token

La integración nueva debe preferir el endpoint:

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

Respuesta:

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

El token lo firma el Auth Service, lleva identidad y grupos, y es de corta validez. El consumidor no necesita reenviar la clave AD en cada llamada.

### Configuración obligatoria

Defina una clave fuerte y manténgala fuera de Git:

`bash
export AUTH_TOKEN_SECRET='una-clave-aleatoria-de-al-menos-32-bytes'
export AUTH_TOKEN_ISSUER='auth-service'
export AUTH_TOKEN_TTL_SECONDS='900'
`

Genere una clave adecuada, por ejemplo:

`bash
openssl rand -base64 48
`

### Uso del token

Tras obtener el token:

`bash
curl -i \
  -H "Authorization: Bearer <JWT>" \
  https://auth.homelab.local/api/v1/identity/me
`

Los endpoints `/me`, `/groups`, `/provider` y `/authenticated` aceptan Bearer Token. HTTP Basic sigue disponible para integraciones existentes.

### Seguridad

- No guarde la clave AD en el ERP.
- No registre el JWT ni el header Authorization en logs.
- Use HTTPS entre consumidores y Auth Service.
- Mantenga el TTL corto.
- La misma clave de validación debe protegerse como secreto de infraestructura.
- Para múltiples instancias, todas deben usar la misma clave e issuer.
- El token representa identidad/autorización del momento de emisión; cambios de grupos AD valen con un token nuevo.

### Compatibilidad

`POST /api/v1/identity/authenticate` sigue devolviendo solo `IdentityDto`, preservando consumidores.

Para integraciones nuevas, use `POST /api/v1/identity/token`.
