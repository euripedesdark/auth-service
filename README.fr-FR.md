# BrasilCloud Auth Service

**Service central d’authentification et d’identité** — valide les utilisateurs contre Active Directory (AD/LDAP) et renvoie identité + groupes vers ERP, firewall, proxy et autres services. Contrat **REST + HTTP Basic**, avec **Bearer JWT courte durée** pour les intégrations modernes. Pas un serveur OAuth2/OIDC.

## Présentation

Point unique d’authentification de l’écosystème BrasilCloud (utilisé par Brasil SaaS ERP). Authentifie sur l’AD via LDAPS. Les groupes AD sont des données d’identité : **l’autorisation appartient à chaque système consommateur**.

## Stack

| Couche | Technologie |
|---|---|
| Langage | Java 21 |
| Framework | Spring Boot 3.3.5 (Web, Security, Actuator) |
| Annuaire | Spring LDAP |
| Tokens | JWT court (oauth2-jose) |
| Docs API | springdoc-openapi (Swagger UI) |
| Build | Maven |
| Port | `8181` |

## Lancer

```bash
export AUTH_LDAP_URL='ldaps://100.100.100.100:636'
export AUTH_LDAP_BASE='DC=homelab,DC=local'
export AUTH_LDAP_USER_DN='CN=svc-auth,OU=Service Accounts,DC=homelab,DC=local'
export AUTH_LDAP_PASSWORD='MOT_DE_PASSE_DU_COMPTE'
export AUTH_LDAP_USER_SEARCH_BASE='OU=Usuarios,DC=homelab,DC=local'

mvn clean package
java -jar target/auth-service-1.0.0-SNAPSHOT.jar
```

Dév : `mvn spring-boot:run`. Port : `SERVER_PORT=8181`. En production derrière HTTPS/reverse proxy.

## Configuration

| Variable | Défaut | Objet |
|---|---|---|
| `AUTH_LDAP_URL` | — | Endpoint LDAP sécurisé |
| `AUTH_LDAP_BASE` | — | Base LDAP |
| `AUTH_LDAP_USER_DN` | — | Compte de service (non-admin) |
| `AUTH_LDAP_PASSWORD` | — | Mot de passe (**hors Git**) |
| `AUTH_LDAP_USER_SEARCH_BASE` | — | Base des utilisateurs |
| `AUTH_LDAP_REQUIRE_SECURE` | `true` | Exige `ldaps://` |
| `AUTH_TOKEN_SECRET` | — | Clé JWT (32+ octets, hors Git) |
| `AUTH_TOKEN_TTL_SECONDS` | `900` | Durée du JWT |
| `SERVER_PORT` | `8181` | Port HTTP |

## Endpoints

| Méthode | Route | Usage |
|---|---|---|
| `POST` | `/api/v1/identity/authenticate` | Login |
| `POST` | `/api/v1/identity/token` | Login avec Bearer JWT |
| `GET` | `/api/v1/identity/me` | Identité |
| `GET` | `/api/v1/identity/groups` | Groupes |
| `GET` | `/actuator/health` | Santé (public) |
| `GET` | `/swagger-ui.html` | Docs interactive |

`401` = mauvais identifiants, `503` = AD/service indisponible (ne jamais traiter 503 comme mot de passe erroné). Ne jamais journaliser mots de passe AD ni JWT.

## Guides de Déploiement et d’Intégration

| Langue | Guide |
|---|---|
| PT-BR | docs/GUIA-INTEGRACAO-AUTH-SERVICE.pt-BR.md |
| EN-US | docs/GUIA-INTEGRACAO-AUTH-SERVICE.en-US.md |
| ES-ES | docs/GUIA-INTEGRACAO-AUTH-SERVICE.es-ES.md |
| FR-FR | docs/GUIA-INTEGRACAO-AUTH-SERVICE.fr-FR.md |

## Prérequis : LDAP / LDAPS / Active Directory

- **Linux :** Samba Active Directory — guide complet :
  https://medium.com/meetcyber/full-guide-deploying-samba-active-directory-on-fedora-and-rocky-with-hybrid-dns-and-gpo-support-30f47fab1e90
- **Windows :** Windows Server Active Directory avec LDAPS activé.

Évaluable sans l’environnement d’entreprise complet ; en production, utilisez-le intégré à un annuaire LDAP/LDAPS, avec un compte de service sans privilèges.

## Soutenir le Projet

BrasilCloud Auth Service est open source maintenu par un seul développeur. S’il vous a aidé, vous, votre entreprise ou votre équipe, pensez à le soutenir.

**PIX (Brésil uniquement) :**

```
24adc62c-b073-4587-974d-03fe35f6733f
```

Tout montant paie serveurs et certificats. N’achète aucun SLA sur aucune issue.

### Virement international

PIX ne fonctionne pas hors du Brésil. **Depuis une banque américaine**, virement domestique. **D’ailleurs**, virement Swift international.

| | |
|---|---|
| **Nom** | Euripedes Batista de Paiva Junior |
| **Type de compte** | Checking |
| **Routing number** (wire et ACH) | `101019628` |
| **Numéro de compte** | `215822927677` |
| **Banque** | Wise US Inc, 108 W 13th St, Wilmington, DE, 19801, United States |
| **SWIFT/BIC** | `TRWIUS35XXX` |

## Assistance

- Email : euripedesdark@gmail.com
- GitHub Issues : https://github.com/euripedesdark/auth-service/issues
- Docs : `docs/`

## Contribuer

1. Ouvrez une issue avant d’écrire du code.
2. Créez une branche descriptive.
3. Commit par sujet et lancez les tests (`mvn test`).
4. Ouvrez la Pull Request vers `main`.

## Licence

Ce projet est sous **GNU AGPL v3**. Voir `LICENSE.fr-FR.md` (note en français ; texte officiel en anglais dans `LICENSE.md`).

(c) Euripedes Batista de Paiva Junior
