# Astral Auth Service

**Service Central d'Identité, d'Authentification et de Gestion des Privilèges Linux** — Développé en **Rust (Axum / Tokio)** avec une architecture orientée objet, intégré nativement à Samba 4 Active Directory DC via LDAPS, émetteur de jetons JWT à courte durée de vie et gestionnaire centralisé des privilèges du système d'exploitation hôte (`wheel`/`sudo`) pour la plateforme Astral.

---

## 🌟 Fonctionnalités Principales

1. **Authentification Centralisée d'Entreprise** :
   - Connexion sécurisée via LDAPS (`ldaps://127.0.0.1:636`) avec chiffrement TLS.
   - Recherche LDAP des utilisateurs, des groupes (`memberOf`) et validation des mots de passe par liaison utilisateur.
   - Émission de jetons JWT signés HMAC-SHA256 contenant `identityId`, `username`, `provider`, `groups`, `isAdmin` et `hasWheel`.

2. **Service de Vérification et Synchronisation des Privilèges Linux (`wheel`)** :
   - **Audit de Samba Active Directory** : Découverte dynamique des comptes administrateurs (`Administrator`, membres de `Domain Admins`, `Administrators` et `Enterprise Admins`).
   - **Vérification sur l'Hôte Linux** : Inspection de `/etc/group`, `/etc/passwd` et du sous-système NSS (`getent group wheel`, `id -nG`) pour confirmer si les administrateurs AD disposent des permissions `wheel` Linux.
   - **Synchronisation Automatisée** : Permet d'ajouter les administrateurs d'entreprise au groupe `wheel` (`usermod -aG wheel`) ou de les créer localement (`useradd -M -s /bin/bash -G wheel`), garantissant la conformité opérationnelle.
   - **Intégration du Module BIND AD-DNS** : Validation des utilisateurs issus des requêtes et liaisons LDAP du module `astral-ad-dns`, avec application des mêmes règles de conformité.

3. **Politique Stricte d'Isolation PostgreSQL** :
   - Les superutilisateurs de base de données PostgreSQL (`rolsuper = true`) sont strictement confinés au niveau de la base de données.
   - Interdiction formelle d'attribuer des privilèges `wheel` ou un accès terminal Linux aux utilisateurs de bases de données (`HTTP 403 Forbidden`).

---

## 🛠️ Stack Technologique

| Couche | Technologie |
|---|---|
| Langage | Rust 2021 (v1.99+) |
| Framework Web | Axum 0.8 / Runtime Asynchrone Tokio |
| Annuaire LDAP | `ldap3` (LDAPS sécurisé) |
| Chiffrement / Jetons | `jsonwebtoken` 11.1 / `chrono` |
| Intégration OS | `/etc/group`, `/etc/passwd`, `getent`, `usermod`, `useradd` |
| Interface Web Admin | React 19 / PrimeReact / Vite (i18n : pt-BR, en-US, es-ES, fr-FR) |
| Port de Service | `8181` (Nginx TLS inverse sur le port `8443`) |

---

## 🚀 Points de Terminaison de l'API REST

### Authentification et Session
- `POST /api/v1/identity/authenticate` : Authentifie les identifiants et configure les cookies de session SSO.
- `POST /api/v1/identity/token` : Authentifie et renvoie un jeton Bearer JWT avec l'objet `Identity`.
- `GET /api/v1/identity/me` : Valide le jeton Bearer des en-têtes et renvoie les claims.
- `POST /api/v1/identity/logout` : Efface les cookies de session.
- `GET /health` & `/actuator/health` : Bilan de santé du service et statut de Samba AD.

### Gestion des Privilèges Linux (Wheel / Samba AD & BIND)
- `GET /api/v1/privileges/status` : Renvoie le rapport d'audit complet de Samba AD, les membres `wheel` et l'état de conformité.
- `GET /api/v1/privileges/verify?username={user}&provider={prov}` : Vérifie ponctuellement la permission `wheel` d'un utilisateur sous Linux.
- `POST /api/v1/privileges/verify-bind` : Valide les utilisateurs provenant du module `astral-ad-dns` par liaison LDAP.
- `POST /api/v1/privileges/sync-wheel` : Synchronise un administrateur AD dans le groupe `wheel` de l'hôte Linux.

---

## 🔒 Exemples d'Audit et de Synchronisation via cURL

```bash
# Vérifier le rapport d'audit
curl -s http://127.0.0.1:8181/api/v1/privileges/status | jq .

# Vérifier la permission d'un utilisateur spécifique
curl -s "http://127.0.0.1:8181/api/v1/privileges/verify?username=Administrator" | jq .

# Synchroniser l'administrateur AD dans le groupe wheel de Linux
curl -s -X POST http://127.0.0.1:8181/api/v1/privileges/sync-wheel \
  -H "Content-Type: application/json" \
  -d '{"username":"Administrator","createLinuxUserIfMissing":true}' | jq .
```
