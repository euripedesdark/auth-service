# 🔐 Auth Service

> 🛡️ Serviço centralizado de autenticação e resolução de identidade para aplicações corporativas, com integração a Active Directory / LDAP, autenticação stateless e emissão de JWT de curta duração.

![Java](https://img.shields.io/badge/Java-21-orange?style=for-the-badge&logo=openjdk) ![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3.5-6DB33F?style=for-the-badge&logo=springboot) ![Security](https://img.shields.io/badge/Spring%20Security-enabled-6DB33F?style=for-the-badge&logo=springsecurity) ![LDAP](https://img.shields.io/badge/Active%20Directory-LDAP%2FLDAPS-0078D4?style=for-the-badge&logo=microsoft) ![API](https://img.shields.io/badge/API-REST%20%2B%20OpenAPI-85EA2D?style=for-the-badge&logo=openapi)

## 🚀 Visão geral

O Auth Service é um microserviço dedicado à autenticação e resolução de identidade. Ele separa a validação da identidade do usuário das regras funcionais de cada sistema consumidor.

### 🎯 Responsabilidades
- 🔑 Autenticar usuários contra Active Directory / LDAP.
- 👤 Resolver a identidade autenticada.
- 👥 Obter grupos associados ao usuário.
- 🏷️ Identificar o provider de autenticação.
- 🎟️ Emitir JWT Bearer de curta duração.
- 🔒 Proteger APIs com Spring Security.
- 🌐 Disponibilizar API REST.
- 📚 Disponibilizar OpenAPI / Swagger.
- ❤️ Disponibilizar health checks.
- 📊 Disponibilizar métricas via Actuator.
- ⚡ Operar sem sessão HTTP tradicional.

## 🧩 Arquitetura

```text
                         +----------------------+
                         | Active Directory     |
                         | LDAP / LDAPS         |
                         +----------^-----------+
                                    |
                                  LDAPS
                                    |
                         +----------+-----------+
                         |     Auth Service     |
                         |     Spring Boot      |
                         |        :8181         |
                         +----^-----------^-----+
                              |           |
                         HTTPS/REST       |
                              |           |
                    +---------+           +----------+
                    |                                |
             +------+-------+                 +------+-------+
             |     ERP      |                 | Firewall /   |
             | / Aplicação  |                 | Proxy/Gateway|
             +------+-------+                 +--------------+
                    |
                  JDBC
                    |
             +------+-------+
             | PostgreSQL / |
             |      DB      |
             +--------------+
```

> 💡 O Auth Service autentica a identidade. Cada sistema consumidor continua responsável por seus dados, permissões funcionais e regras de negócio.

## ✨ Recursos

### 🔐 Active Directory
Autenticação através de provider dedicado para AD/LDAP, com suporte a conexão segura LDAPS.

### 🎟️ JWT Bearer
O endpoint `/api/v1/identity/token` valida as credenciais e emite um token de curta duração. O TTL padrão é de **900 segundos (15 minutos)**.

Claims principais:
- 🆔 `sub` / identityId
- 👤 `username`
- 🏷️ `provider`
- 👥 `groups`
- 🕐 `iat`
- ⏳ `exp`
- 🏢 `iss`

### 🔑 HTTP Basic
Os endpoints de consulta de identidade suportam HTTP Basic Authentication.

### 🛡️ Spring Security
- Stateless session policy
- HTTP Basic
- OAuth2 Resource Server / JWT
- Respostas 401 e 403
- HSTS
- Content-Type protection
- Frame protection
- Deny-by-default para rotas não autorizadas

## 📡 API

| Método | Endpoint | Finalidade |
|---|---|---|
| POST | `/api/v1/identity/authenticate` | Autenticação direta |
| POST | `/api/v1/identity/token` | Autenticação + emissão JWT |
| GET | `/api/v1/identity/me` | Identidade autenticada |
| GET | `/api/v1/identity/groups` | Identidade + grupos |
| GET | `/api/v1/identity/provider` | Identidade + provider |
| GET | `/api/v1/identity/authenticated` | Estado/identidade autenticada |
| GET | `/actuator/health` | Health check |

### 🔓 POST `/api/v1/identity/authenticate`
```json
{
  "username": "usuario",
  "password": "senha",
  "provider": "AD"
}
```

Resposta:
```json
{
  "identityId": "CN=Usuario,OU=Usuarios,DC=example,DC=local",
  "username": "usuario",
  "provider": "AD",
  "groups": ["ERP-Administradores", "ERP-Financeiro"]
}
```

### 🎟️ POST `/api/v1/identity/token`
```json
{
  "username": "usuario",
  "password": "senha",
  "provider": "AD"
}
```

Uso do token:
```http
Authorization: Bearer <TOKEN>
```

## 📚 OpenAPI / Swagger

- 📄 OpenAPI: `/v3/api-docs`
- 🧭 Swagger UI: `/swagger-ui.html`
- 📑 Especificação versionada: `docs/openapi.yaml`

> ⚠️ Em produção, avalie a exposição da documentação da API.

## ⚙️ Configuração

### 🔴 Obrigatórias

| Variável | Finalidade |
|---|---|
| `AUTH_LDAP_URL` | Endpoint LDAP/LDAPS |
| `AUTH_LDAP_BASE` | Base LDAP |
| `AUTH_LDAP_USER_DN` | DN da conta de serviço |
| `AUTH_LDAP_PASSWORD` | Senha da conta de serviço |
| `AUTH_LDAP_USER_SEARCH_BASE` | Base de pesquisa de usuários |
| `AUTH_TOKEN_SECRET` | Segredo dos tokens |

### 🟡 Opcionais

| Variável | Padrão | Finalidade |
|---|---:|---|
| `SERVER_PORT` | `8181` | Porta HTTP |
| `AUTH_LDAP_REQUIRE_SECURE` | `true` | Exigir LDAP seguro |
| `AUTH_LDAP_CONNECT_TIMEOUT_MS` | `5000` | Timeout de conexão |
| `AUTH_LDAP_READ_TIMEOUT_MS` | `5000` | Timeout de leitura |
| `AUTH_TOKEN_ISSUER` | `auth-service` | Issuer JWT |
| `AUTH_TOKEN_TTL_SECONDS` | `900` | Validade do JWT |

## 🔒 Segurança

### ❌ Nunca versionar
- Senhas LDAP
- Segredos JWT
- Tokens
- Authorization headers
- Certificados/chaves privadas
- Credenciais reais de produção

### ✅ Recomendações
- 🔐 Secret Manager / Vault / secrets do CI/CD
- 🔒 HTTPS entre consumidores e Auth Service
- 🔐 LDAPS entre Auth Service e AD
- 🧱 Restrição de acesso à porta 8181
- 🧾 Logs sem senhas
- ⏱️ Timeouts definidos
- 👤 Conta de serviço AD com privilégio mínimo

## 🧪 Desenvolvimento

### Pré-requisitos
- ☕ Java 21
- 🧰 Maven
- 🏢 Active Directory / LDAP para integração
- 🔐 LDAPS recomendado em ambientes reais
- 🌐 DNS funcional

### 🔨 Build
```bash
mvn clean package
```

### 🧪 Testes
```bash
mvn clean test
```

### ▶️ Execução
```bash
export AUTH_LDAP_URL='ldaps://ad.example.local:636'
export AUTH_LDAP_BASE='DC=example,DC=local'
export AUTH_LDAP_USER_DN='CN=svc-auth,OU=Service Accounts,DC=example,DC=local'
export AUTH_LDAP_PASSWORD='CHANGE_ME'
export AUTH_LDAP_USER_SEARCH_BASE='OU=Users,DC=example,DC=local'
export AUTH_TOKEN_SECRET='CHANGE_ME_TO_A_SECURE_SECRET'
export AUTH_TOKEN_ISSUER='auth-service'
export AUTH_TOKEN_TTL_SECONDS='900'
export AUTH_LDAP_REQUIRE_SECURE='true'
export SERVER_PORT='8181'

mvn spring-boot:run
```

### 📦 JAR
```bash
java -jar target/auth-service-1.0.0-SNAPSHOT.jar
```

## ❤️ Health Check
```bash
curl -i http://localhost:8181/actuator/health
```

Health check da aplicação não substitui um teste de autenticação contra o AD.

## 🔬 Teste de autenticação
```bash
curl -i -X POST \
  -H 'Content-Type: application/json' \
  -d '{"username":"usuario","password":"SENHA","provider":"AD"}' \
  http://localhost:8181/api/v1/identity/authenticate
```

## 🎟️ Teste de JWT
```bash
curl -i -X POST \
  -H 'Content-Type: application/json' \
  -d '{"username":"usuario","password":"SENHA","provider":"AD"}' \
  http://localhost:8181/api/v1/identity/token
```

## 🧱 Estrutura
```text
auth-service/
├── .github/workflows/build.yml
├── docs/
│   ├── GUIA-INTEGRACAO-AUTH-SERVICE.md
│   └── openapi.yaml
├── src/main/java/com/euripedes/authservice/
│   ├── api/
│   ├── audit/
│   ├── config/
│   ├── contract/
│   ├── model/
│   ├── provider/
│   ├── resolver/
│   └── service/
├── src/main/resources/application.yml
├── src/test/
├── pom.xml
└── README.md
```

## 🧩 Componentes

| Componente | Responsabilidade |
|---|---|
| `IdentityController` | API REST de identidade |
| `AuthenticationService` | Orquestração da autenticação |
| `TokenService` | Emissão de JWT |
| `AdProvider` | Integração com Active Directory |
| `IdentityResolver` | Resolução da identidade |
| `GroupResolver` | Resolução de grupos |
| `SecurityConfig` | Políticas de segurança |
| `TokenConfig` | Configuração de tokens |
| `LdapConfig` | Configuração LDAP |
| `AuditService` | Auditoria |
| `ApiExceptionHandler` | Tratamento de exceções |

## 🔄 Fluxo
```text
1️⃣ Cliente envia credenciais
        ↓
2️⃣ Auth Service recebe a requisição
        ↓
3️⃣ Provider AD autentica
        ↓
4️⃣ Identity Resolver resolve identidade
        ↓
5️⃣ Group Resolver obtém grupos
        ↓
6️⃣ Auth Service retorna identidade ou JWT
```

## 🏢 Integração corporativa

Pode ser integrado a:
- 🏢 ERP
- 🔥 Firewalls
- 🌐 Proxies
- 🖥️ Portais
- 🧩 APIs internas
- 🔐 Gateways
- ⚙️ Microserviços
- 🛠️ Ferramentas administrativas

> 💡 O consumidor deve mapear grupos AD para suas próprias permissões. Regras específicas de ERP, firewall ou outro produto não devem ser acopladas ao Auth Service.

## ⚠️ Códigos HTTP

| Código | Significado |
|---:|---|
| `200` | Operação concluída |
| `400` | Requisição inválida |
| `401` | Não autenticado / credencial inválida |
| `403` | Acesso negado |
| `503` | Provider de identidade indisponível |

> 🚨 Não trate `503` como senha errada. Indisponibilidade de infraestrutura é diferente de credencial inválida.

## 🩺 Observabilidade

O projeto utiliza Spring Boot Actuator e expõe:
- ❤️ health
- ℹ️ info
- 📊 metrics

Os endpoints expostos devem ser protegidos de acordo com a política do ambiente.

## 🔄 CI/CD

O repositório possui workflow em:
```text
.github/workflows/build.yml
```

Ele permite automatizar a validação do build através do GitHub Actions.

## 📖 Documentação

- 📘 `docs/GUIA-INTEGRACAO-AUTH-SERVICE.md` — implantação e integração
- 📑 `docs/openapi.yaml` — contrato OpenAPI

## 🗺️ Evolução

Possíveis evoluções futuras:
- 🔑 rotação automatizada de chaves
- 🏢 múltiplos domínios/florestas AD
- 🔗 múltiplos providers
- 🔄 alta disponibilidade
- ⚖️ load balancing
- 📊 observabilidade ampliada
- 🧾 auditoria ampliada
- 🚦 rate limiting
- 🛡️ controles adicionais de proteção da API
- 🔁 mecanismos controlados de revogação

> ℹ️ Roadmap não significa funcionalidade disponível na versão atual.

## 📌 Status

🟢 **Projeto privado em desenvolvimento.**

Foco atual: autenticação corporativa, resolução de identidade, integração Active Directory/LDAP e emissão de tokens JWT.

## 🔒 Licença

### 🚫 Proprietária — Todos os direitos reservados

Este software é **proprietário e fechado**.

Sem autorização expressa do titular dos direitos, não é concedida permissão para:
- ❌ copiar
- ❌ redistribuir
- ❌ modificar
- ❌ publicar
- ❌ sublicenciar
- ❌ comercializar
- ❌ criar obras derivadas

O acesso ao código-fonte por meio do repositório privado **não constitui concessão de licença de uso, distribuição ou exploração comercial**.

Este projeto **não é open source** e não está disponibilizado sob MIT, Apache-2.0, GPL, AGPL, BSD ou outra licença aberta.

Qualquer uso além das permissões expressamente concedidas pelo titular depende de autorização específica.

---

🔐 **Identity first. Security by design.** 🛡️

🚀 **Private • Enterprise • Secure • Stateless**