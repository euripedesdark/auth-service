# Astral Auth Service

🇧🇷 **Open Source Authentication Service** — autenticação centralizada e identidade (LDAP/AD) para ERP, firewall, proxy e outros serviços, com REST + JWT de curta duração.

🇺🇸**Open Source Authentication Service** — centralized authentication and identity (LDAP/AD) for ERP, firewall, proxy, and other services, using REST + short-lived JWT.

🇧🇷 Este projeto está sob **GNU AGPL v3**. Ver [LICENSE.md](LICENSE.md).

🇧🇷 Documentação completa em português: [README.pt-BR.md](README.pt-BR.md)
---


🇺🇸 This project is under the **GNU AGPL v3**. See [LICENSE.md](LICENSE.md).

🇺🇸 Complete documentation in Engiish : [README.en-US.md](README.en-US.md) |
---


## Escolha o seu idioma / Choose your language

| | Idioma | Documentação |
|---|---|---|
| 🇧🇷 | **Português (Brasil)** | [README.pt-BR.md](README.pt-BR.md) |
| 🇺🇸 | **English** | [README.en-US.md](README.en-US.md) |
| 🇪🇸 | **Español** | [README.es-ES.md](README.es-ES.md) |
| 🇫🇷 | **Français** | [README.fr-FR.md](README.fr-FR.md) |

🇧🇷 [Português](README.pt-BR.md) · 🇺🇸 [English](README.en-US.md) · 🇪🇸 [Español](README.es-ES.md) · 🇫🇷 [Français](README.fr-FR.md)

---

## Apoie o Projeto

Astral Auth Service é um Auth Service Open Source mantido por um único desenvolvedor.

Se este projeto ajudou você, sua empresa ou sua equipe, considere apoiar o desenvolvimento.

**PIX:** `24adc62c-b073-4587-974d-03fe35f6733f`

### Transferência internacional (Wise)

A chave PIX não funciona fora do Brasil.

**Se você envia de um banco dos Estados Unidos**, use estes dados para uma transferência doméstica; **de qualquer outro lugar**, faça uma transferência internacional Swift.

| | |
|---|---|
| **Nome** | Euripedes Batista de Paiva Junior |
| **Tipo de conta** | Checking |
| **Routing number** (wire e ACH) | `101019628` |
| **Número da conta** | `215822927677` |
| **Nome e endereço do banco** | Wise US Inc, 108 W 13th St, Wilmington, DE, 19801, United States |
| **SWIFT/BIC** | `TRWIUS35XXX` |

Os dados completos, com as instruções em quatro idiomas, estão nos READMEs:

🇧🇷 [README.pt-BR.md](README.pt-BR.md) · 🇺🇸 [README.en-US.md](README.en-US.md) · 🇪🇸 [README.es-ES.md](README.es-ES.md) · 🇫🇷 [README.fr-FR.md](README.fr-FR.md)

---

## Licença

**GNU Affero General Public License v3.0 (AGPLv3).** O texto completo e inalterado está em [LICENSE.md](LICENSE.md).

A AGPLv3 exige que o código-fonte seja oferecido a quem usa o programa, inclusive quando o uso é **pela rede** — por isso o Affero. Para um serviço de autenticação consumido por HTTPS, essa cláusula é a que importa: quem autentica neste serviço tem direito ao código.

(c) 2026 Astral Auth Service — **Criado por: Euripedes Batista de Paiva Junior**

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
