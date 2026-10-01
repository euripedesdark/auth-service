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
