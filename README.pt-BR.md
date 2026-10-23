# BrasilCloud Auth Service

**Serviço central de autenticação e identidade** — valida usuários contra Active Directory (AD/LDAP) e devolve identidade + grupos para ERP, firewall, proxy e outros serviços. Contrato **REST + HTTP Basic**, com **Bearer JWT de curta duração** para integrações modernas. Não é servidor OAuth2/OIDC.

---

## Sobre o Projeto

Ponto único de autenticação do ecossistema BrasilCloud (usado pelo Brasil SaaS ERP). Autentica no AD via LDAPS e retorna:

```json
{
  "identityId": "CN=Euripedes Batista,OU=Usuarios,DC=homelab,DC=local",
  "username": "euripedes",
  "provider": "AD",
  "groups": ["ERP-Administradores", "ERP-Financeiro"]
}
```

Grupos do AD são dados de identidade: **autorização é responsabilidade de cada sistema consumidor**.

## Stack

| Camada | Tecnologia |
|---|---|
| Linguagem | Java 21 |
| Framework | Spring Boot 3.3.5 (Web, Security, Actuator) |
| Diretório | Spring LDAP |
| Tokens | JWT curto (oauth2-jose) |
| Docs API | springdoc-openapi (Swagger UI) |
| Build | Maven |
| Porta padrão | `8181` |

## Como Subir

```bash
export AUTH_LDAP_URL='ldaps://100.100.100.100:636'
export AUTH_LDAP_BASE='DC=homelab,DC=local'
export AUTH_LDAP_USER_DN='CN=svc-auth,OU=Service Accounts,DC=homelab,DC=local'
export AUTH_LDAP_PASSWORD='SENHA_DA_CONTA_DE_SERVICO'
export AUTH_LDAP_USER_SEARCH_BASE='OU=Usuarios,DC=homelab,DC=local'

mvn clean package
java -jar target/auth-service-1.0.0-SNAPSHOT.jar
```

Desenvolvimento: `mvn spring-boot:run`. Porta: `SERVER_PORT=8181`. Em produção, rode atrás de HTTPS/reverse proxy; não exponha a 8181.

## Configuração

| Variável | Padrão | Finalidade |
|---|---|---|
| `AUTH_LDAP_URL` | — | Endpoint LDAP seguro (`ldaps://`) |
| `AUTH_LDAP_BASE` | — | Base LDAP |
| `AUTH_LDAP_USER_DN` | — | Conta de serviço (não-admin) |
| `AUTH_LDAP_PASSWORD` | — | Senha da conta (**fora do Git**) |
| `AUTH_LDAP_USER_SEARCH_BASE` | — | Base de pesquisa de usuários |
| `AUTH_LDAP_REQUIRE_SECURE` | `true` | Exige `ldaps://` |
| `AUTH_TOKEN_SECRET` | — | Chave do JWT (≥32 bytes, fora do Git) |
| `AUTH_TOKEN_TTL_SECONDS` | `900` | Validade do JWT |
| `SERVER_PORT` | `8181` | Porta HTTP |

## Endpoints

| Método | Rota | Uso |
|---|---|---|
| `POST` | `/api/v1/identity/authenticate` | Login (username/password/provider=AD) |
| `POST` | `/api/v1/identity/token` | Login retornando Bearer JWT |
| `GET` | `/api/v1/identity/me` | Identidade (Basic ou Bearer) |
| `GET` | `/api/v1/identity/groups` | Grupos do usuário |
| `GET` | `/actuator/health` | Saúde (público) |
| `GET` | `/swagger-ui.html` | Docs interativa |

Consumidor: `401` = credencial inválida, `503` = AD/serviço indisponível (nunca trate 503 como senha errada). Nunca grave senha AD nem JWT em log.

## Guias de Implantação e Integração

| Idioma | Guia |
|---|---|
| PT-BR | docs/GUIA-INTEGRACAO-AUTH-SERVICE.pt-BR.md |
| EN-US | docs/GUIA-INTEGRACAO-AUTH-SERVICE.en-US.md |
| ES-ES | docs/GUIA-INTEGRACAO-AUTH-SERVICE.es-ES.md |
| FR-FR | docs/GUIA-INTEGRACAO-AUTH-SERVICE.fr-FR.md |

## Requisitos: LDAP / LDAPS / Active Directory

- **Linux:** Samba Active Directory — guia completo:
  https://medium.com/meetcyber/full-guide-deploying-samba-active-directory-on-fedora-and-rocky-with-hybrid-dns-and-gpo-support-30f47fab1e90
- **Windows:** Windows Server Active Directory com LDAPS habilitado.

Pode ser avaliado sem o ambiente corporativo completo; em produção, use-o integrado a um diretório LDAP/LDAPS, com conta de serviço sem privilégios administrativos.

## Apoie o Projeto

BrasilCloud Auth Service é Open Source mantido por um único desenvolvedor. Se ajudou você, sua empresa ou sua equipe, considere apoiar.

**PIX:**

```
24adc62c-b073-4587-974d-03fe35f6733f
```

Doação de qualquer valor paga servidor e certificados. Não compra garantia de prazo em nenhuma issue.

### Transferência internacional

A chave PIX não funciona fora do Brasil. **De banco dos Estados Unidos**, transferência doméstica. **De qualquer outro lugar**, Swift internacional.

| | |
|---|---|
| **Nome** | Euripedes Batista de Paiva Junior |
| **Tipo de conta** | Checking |
| **Routing number** (wire e ACH) | `101019628` |
| **Número da conta** | `215822927677` |
| **Banco** | Wise US Inc, 108 W 13th St, Wilmington, DE, 19801, United States |
| **SWIFT/BIC** | `TRWIUS35XXX` |

## Suporte

- Email: euripedesdark@gmail.com
- GitHub Issues: https://github.com/euripedesdark/auth-service/issues
- Docs: `docs/`

## Como Contribuir

1. Abra uma issue antes de escrever código.
2. Crie uma branch com nome descritivo.
3. Commite por tema e rode os testes (`mvn test`).
4. Abra o Pull Request contra a `main`.

## Licença

Este projeto está sob **GNU AGPL v3**. Ver `LICENSE.pt-BR.md` (nota em português; texto oficial em inglês em `LICENSE.md`).

(c) Euripedes Batista de Paiva Junior

## 🔐 Política Unificada de Acesso e Autenticação do Ecossistema Astral

O **Astral Auth Service** é o núcleo emissor de identidade e orquestrador de confiança para a plataforma Astral. Ele atua sob as seguintes diretrizes:

### 1. Papel Central como Provedor de Identidade (IdP)
- **Validação contra Active Directory / LDAP**: Autentica credenciais de operadores e usuários corporativos diretamente no Samba 4 AD via LDAPS (porta 636), mapeando grupos de segurança (`Domain Admins`, `Schema Admins`, etc.).
- **Integração com PostgreSQL**: Validação de contas de superusuários do banco de dados local (`rolsuper = true`).
- **Emissão e Assinatura de Tokens JWT**: Gera tokens Bearer assinados com o segredo corporativo compartilhado (`AUTH_TOKEN_SECRET` em `/etc/auth-service/auth.env`), além de cookies de sessão (`sso_token` e `astral_token`).

### 2. Exceção do Acesso Web IAM
- **Acesso Administrativo Autônomo**: A interface web de IAM do `auth-service` retém credenciais e mecanismo de autenticação administrativo independente/direto.
- **Justificativa Arquitetural**: O `auth-service` é o módulo responsável por configurar a conectividade inicial com o AD, orquestrar a autoridade de certificação (PKI) e gerar certificados TLS. Se ele dependesse exclusivamente do AD para login, uma indisponibilidade ou provisionamento inicial do AD bloquearia a própria ferramenta necessária para restabelecer a infraestrutura.

### 3. Consumo pelos Demais Módulos Astral
- Os módulos (`astral-ad-dns`, `astral-dbm`, `astral-internet-security`, `astral-network`, `astral-platform-hci`, `astral-traffic-guard`) consomem tokens emitidos por este serviço ou validam os operadores via suas 4 vias padronizadas (Samba AD, Linux Sudo, PostgreSQL Superuser ou SSO).
