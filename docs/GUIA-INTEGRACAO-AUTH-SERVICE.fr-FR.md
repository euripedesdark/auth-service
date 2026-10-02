# Guide de Déploiement et d’Intégration — Auth Service

## 1. Objectif

L’Auth Service est le service central d’authentification et de résolution d’identité pour les systèmes qui doivent valider les utilisateurs contre Active Directory (AD/LDAP).

Le contrat principal est **REST + HTTP Basic Authentication**, avec en plus la prise en charge de **Bearer JWT courte durée** pour les intégrations modernes. L’Auth Service n’implémente pas OAuth2/OIDC comme serveur d’autorisation.

Flux actuel :

~~~text
ERP / SGDB-adapter / Firewall / Proxy / autre service
                    |
                    | HTTPS + HTTP Basic
                    v
             Auth Service :8181
                    |
                    | LDAPS
                    v
             Active Directory
~~~

L’Auth Service renvoie l’identité :

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

> **Important :** les groupes AD sont renvoyés comme données d’identité. Le service consommateur reste responsable d’appliquer ses propres règles d’autorisation.

---

## 2. Données de déploiement par défaut

### Port HTTP

L’application Spring Boot utilise le **port 8181 par défaut**, car aucun autre port n’est défini dans application.yml.

Pour changer :

~~~bash
SERVER_PORT=8181
~~~

ou :

~~~yaml
server:
  port: 8181
~~~

En production, placez l’Auth Service derrière HTTPS/reverse proxy et n’exposez pas le port 8181 directement au réseau des utilisateurs.

### Adresse d’exemple

En supposant :

- Auth Service : 192.168.2.50
- Port : 8181
- AD/DC : 100.100.100.100
- domaine AD : homelab.local

L’adresse interne sera :

~~~text
http://192.168.2.50:8181
~~~

En production :

~~~text
https://auth.homelab.local
~~~

Le hostname HTTPS n’est qu’un exemple ; il doit être remplacé par le DNS réel de l’installation.

---

## 3. Prérequis

### Auth Service

- Java compatible avec la version du build.
- Maven pour compiler.
- Accès réseau à l’AD.
- Certificat de confiance pour LDAPS.
- Compte de service AD pour les requêtes.
- DNS fonctionnel.
- Heure synchronisée sur les serveurs.

### Active Directory

L’Auth Service doit pouvoir :

1. établir la connexion LDAPS ;
2. authentifier le compte de service ;
3. rechercher les utilisateurs ;
4. interroger l’attribut memberOf ;
5. authentifier le mot de passe présenté par l’utilisateur.

Le compte de service **ne doit pas être un compte administratif de domaine**.

# 4. Configuration de l’Auth Service

La configuration actuelle utilise des variables d’environnement.

## 4.1 Variables obligatoires

| Variable | Exemple | Objet |
|---|---|---|
| AUTH_LDAP_URL | ldaps://100.100.100.100:636 | Endpoint sécurisé |
| AUTH_LDAP_BASE | DC=homelab,DC=local | Base LDAP |
| AUTH_LDAP_USER_DN | CN=svc-auth,OU=Service Accounts,DC=homelab,DC=local | Compte de service |
| AUTH_LDAP_PASSWORD | ******** | Mot de passe du compte |
| AUTH_LDAP_USER_SEARCH_BASE | OU=Usuarios,DC=homelab,DC=local | Base des utilisateurs |

## 4.2 Variables optionnelles

| Variable | Défaut | Objet |
|---|---:|---|
| AUTH_LDAP_REQUIRE_SECURE | true | Exige ldaps:// |
| AUTH_LDAP_CONNECT_TIMEOUT_MS | 5000 | Timeout de connexion |
| AUTH_LDAP_READ_TIMEOUT_MS | 5000 | Timeout de lecture |
| SERVER_PORT | 8181 | Port HTTP |

### Exemple

~~~bash
export AUTH_LDAP_URL='ldaps://100.100.100.100:636'
export AUTH_LDAP_BASE='DC=homelab,DC=local'
export AUTH_LDAP_USER_DN='CN=svc-auth,OU=Service Accounts,DC=homelab,DC=local'
export AUTH_LDAP_PASSWORD='MOT_DE_PASSE_DU_COMPTE'
export AUTH_LDAP_USER_SEARCH_BASE='OU=Usuarios,DC=homelab,DC=local'
export AUTH_LDAP_REQUIRE_SECURE='true'
export AUTH_LDAP_CONNECT_TIMEOUT_MS='5000'
export AUTH_LDAP_READ_TIMEOUT_MS='5000'
export SERVER_PORT='8181'
~~~

**Ne mettez pas le mot de passe du compte de service dans Git.**

---

# 5. LDAPS et certificat

La configuration actuelle exige LDAPS quand :

~~~text
AUTH_LDAP_REQUIRE_SECURE=true
~~~

Dans ce cas, ldaps:// est obligatoire.

N’utilisez pas ldap:// en production quand l’exigence LDAP sécurisé est activée.

Le certificat présenté par le contrôleur de domaine doit être de confiance pour la JVM qui exécute l’Auth Service.

Exemple conceptuel :

~~~text
Auth Service
     |
     | TLS
     v
AD/DC:636
     |
     +-- certificat du serveur AD
~~~

Avant le déploiement, validez :

~~~bash
openssl s_client -connect 100.100.100.100:636 -showcerts
~~~

Le test doit être fait depuis le serveur où l’Auth Service sera exécuté.

# 6. Démarrer l’Auth Service

Après avoir configuré les variables :

~~~bash
mvn clean package
~~~

Exécutez :

~~~bash
java -jar target/auth-service-1.0.0-SNAPSHOT.jar
~~~

Ou, pendant le développement :

~~~bash
mvn spring-boot:run
~~~

L’application sera, par défaut, sur :

~~~text
http://localhost:8181
~~~

---

# 7. Vérification de santé

L’endpoint de health est public :

~~~http
GET /actuator/health
~~~

Test :

~~~bash
curl -i http://localhost:8181/actuator/health
~~~

Cet endpoint ne remplace pas le test d’authentification LDAP. Il sert à vérifier que l’application répond.

---

# 8. Authentification d’utilisateur

L’endpoint principal pour authentifier un utilisateur est :

~~~http
POST /api/v1/identity/authenticate
Content-Type: application/json
~~~

Payload :

~~~json
{
  "username": "euripedes",
  "password": "MOT_DE_PASSE",
  "provider": "AD"
}
~~~

Exemple :

~~~bash
curl -i \
  -X POST \
  -H 'Content-Type: application/json' \
  -d '{\"username\":\"euripedes\",\"password\":\"MOT_DE_PASSE\",\"provider\":\"AD\"}' \
  https://auth.homelab.local/api/v1/identity/authenticate
~~~

Réponse attendue :

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

## 8.1 Ce que le consommateur doit faire de cette réponse

Le système consommateur doit :

1. identifier l’utilisateur ;
2. obtenir les groupes ;
3. retrouver son enregistrement interne, quand il existe ;
4. appliquer ses propres permissions ;
5. créer sa propre session locale, si nécessaire.

L’Auth Service ne doit pas être traité comme base fonctionnelle de l’ERP.

# 9. Requête d’identité en HTTP Basic

Les endpoints GET d’identité exigent l’authentification HTTP Basic.

### Identité

~~~http
GET /api/v1/identity/me
Authorization: Basic <credentials>
~~~

Exemple :

~~~bash
curl -i \
  -u 'euripedes:MOT_DE_PASSE' \
  https://auth.homelab.local/api/v1/identity/me
~~~

### Groupes

~~~bash
curl -i \
  -u 'euripedes:MOT_DE_PASSE' \
  https://auth.homelab.local/api/v1/identity/groups
~~~

### Provider

~~~bash
curl -i \
  -u 'euripedes:MOT_DE_PASSE' \
  https://auth.homelab.local/api/v1/identity/provider
~~~

### État authentifié

~~~bash
curl -i \
  -u 'euripedes:MOT_DE_PASSE' \
  https://auth.homelab.local/api/v1/identity/authenticated
~~~

Les quatre endpoints renvoient le même contrat Identity ; les noms existent pour séparer sémantiquement les usages.

---

# 10. Codes HTTP à traiter

| Code | Signification |
|---:|---|
| 200 | Authentification/requête aboutie |
| 400 | Requête invalide |
| 401 | Identifiants absents ou invalides |
| 403 | Accès refusé |
| 503 | Fournisseur d’identité indisponible |

### Règle importante

Le consommateur **ne doit pas traiter 503 comme mot de passe erroné**.

~~~text
401 -> identifiant invalide / non authentifié
503 -> Auth Service ou AD indisponible
~~~

Cela distingue l’échec d’authentification de la panne d’infrastructure.

---

# 11. Intégration de l’ERP

L’ERP peut utiliser l’Auth Service comme point central d’authentification.

Flux recommandé :

~~~text
Utilisateur
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
  | identité + groupes
  v
Auth Service
  |
  v
ERP
  |
  +--> session locale
  +--> utilisateur interne
  +--> permissions ERP
~~~

## 11.1 Configuration ERP suggérée

Exemple :

~~~properties
auth.service.url=https://auth.homelab.local
auth.service.authenticate-path=/api/v1/identity/authenticate
auth.service.me-path=/api/v1/identity/me
auth.service.groups-path=/api/v1/identity/groups
auth.provider=AD
~~~

Si l’ERP utilise des variables d’environnement :

~~~bash
AUTH_SERVICE_URL=https://auth.homelab.local
AUTH_SERVICE_PROVIDER=AD
AUTH_SERVICE_AUTHENTICATE_PATH=/api/v1/identity/authenticate
AUTH_SERVICE_ME_PATH=/api/v1/identity/me
AUTH_SERVICE_GROUPS_PATH=/api/v1/identity/groups
~~~

## 11.2 Exemple Java/Spring

L’ERP doit avoir un simple client HTTP :

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

DTO :

~~~java
public record IdentityDto(
    String identityId,
    String username,
    String provider,
    List<String> groups
) {}
~~~

---

# 12. Groupes AD et ERP

L’Auth Service renvoie :

~~~json
"groups": [
  "ERP-Administradores",
  "ERP-Financeiro",
  "ERP-Vendas"
]
~~~

L’ERP doit mapper ces groupes vers ses permissions internes.

Exemple :

~~~text
Groupe AD                    Permission ERP

ERP-Administradores   ->    ADMIN
ERP-Financeiro        ->    FINANCEIRO
ERP-Vendas            ->    VENDAS
ERP-Compras           ->    COMPRAS
ERP-Estoque           ->    ESTOQUE
~~~

Ce mapping appartient à l’ERP.

Ne mettez pas de règles ERP dans l’Auth Service.

# 13. Intégration base de données / SGDB

## 13.1 Point important

Un SGDB traditionnel, comme PostgreSQL ou MySQL, **ne doit pas être configuré comme s’il pouvait appeler directement une API REST HTTP à chaque login**, car l’Auth Service est un service REST et non un mécanisme LDAP natif de la base.

Architecture applicative :

~~~text
Application
   |
   +----> Auth Service ----> AD
   |
   +----> PostgreSQL/MySQL
~~~

Ceci est différent de :

~~~text
PostgreSQL ----HTTP----> Auth Service
~~~

La seconde architecture n’est pas une intégration native fournie par l’Auth Service actuel.

---

# 14. ERP + PostgreSQL

Pour un ERP avec PostgreSQL, la répartition est :

~~~text
                  +--> Auth Service --> AD
                  |
Utilisateur --> ERP --+
                  |
                  +--> PostgreSQL
~~~

PostgreSQL stocke :

- données ERP ;
- utilisateurs internes, si l’ERP garde cet enregistrement ;
- liens internes utilisateur/permissions ;
- audit ;
- paramètres fonctionnels.

L’Auth Service est responsable de :

- valider les identifiants AD ;
- résoudre l’identité ;
- renvoyer les groupes AD.

## 14.1 Exemple de configuration

~~~properties
auth.service.url=https://auth.homelab.local
database.url=jdbc:postgresql://db-erp:5432/brasilcloud
database.username=brasilcloud
database.password=********
~~~

Flux :

~~~text
1. L’utilisateur saisit login/mot de passe
2. L’ERP appelle l’Auth Service
3. L’Auth Service valide sur l’AD
4. L’Auth Service renvoie l’identité
5. L’ERP retrouve l’utilisateur interne par username/identityId
6. L’ERP applique les permissions
7. L’ERP ouvre la session
~~~

---

# 15. Systèmes de base devant authentifier des utilisateurs

Si l’objectif est de permettre à un outil d’admin base, comme un client SQL, d’authentifier avec le même AD, il y a deux possibilités.

### Option A — authentification native du SGDB

Le SGDB peut être configuré pour utiliser LDAP/AD directement, quand le produit le permet.

Flux :

~~~text
Client SQL --> SGDB --> LDAP/AD
~~~

Dans ce cas l’Auth Service ne participe pas.

### Option B — via Auth Service

Pour passer obligatoirement par l’Auth Service :

~~~text
Client SQL
     |
     v
Gateway/adapter/proxy d’authentification
     |
     v
Auth Service
     |
     v
AD
~~~

Cela exige un composant intermédiaire qui connaît le protocole attendu par le SGDB et convertit l’authentification en HTTP.

**Le SGDB ne doit pas simplement pointer vers http://auth-service:8181.**

---

# 16. Intégration firewall/proxy

Pour firewall, proxy ou gateway avec sa propre application :

~~~text
Utilisateur
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

Configuration conceptuelle :

~~~text
AUTH_SERVICE_URL=https://auth.homelab.local
AUTH_SERVICE_PROVIDER=AD
AUTH_SERVICE_AUTHENTICATE_PATH=/api/v1/identity/authenticate
~~~

Le firewall/proxy reçoit :

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

Et décide localement :

~~~text
FW-Administradores -> administration
FW-Operadores      -> opération
FW-Auditoria       -> lecture seule
~~~

# 17. Intégrer un nouveau service

Pour ajouter tout nouveau système :

### Étape 1 — Définir l’URL

~~~text
https://auth.exemplo.local
~~~

### Étape 2 — Définir la route d’authentification

~~~text
POST /api/v1/identity/authenticate
~~~

### Étape 3 — Envoyer

~~~json
{
  "username": "usuario",
  "password": "senha",
  "provider": "AD"
}
~~~

### Étape 4 — Valider la réponse

Le service doit exiger :

~~~text
HTTP 200
~~~

et valider :

~~~text
identityId
username
provider
groups
~~~

### Étape 5 — Créer l’identité locale

Le système peut chercher l’utilisateur par username ou, quand un identifiant persistant est nécessaire, par identityId.

### Étape 6 — Appliquer l’autorisation locale

Le service consommateur décide des fonctionnalités autorisées.

---

# 18. Sécurité réseau

En production, n’exposez pas l’endpoint d’authentification sans TLS.

Recommandation :

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

Le port 8181 peut rester restreint au réseau interne ou au reverse proxy.

Exemple :

~~~text
Reverse Proxy -> Auth Service:8181    ALLOW
ERP            -> Auth Service:8181    ALLOW
Firewall       -> Auth Service:8181    ALLOW
Utilisateurs   -> Auth Service:8181    DENY
Internet       -> Auth Service:8181    DENY
~~~

La vraie politique doit être adaptée à la topologie.

---

# 19. DNS recommandé

Créez un nom stable pour le service :

~~~text
auth.homelab.local
~~~

Exemple :

~~~text
auth.homelab.local -> 192.168.2.50
~~~

Les systèmes doivent utiliser le hostname, pas l’IP directement :

~~~text
https://auth.homelab.local
~~~

Cela permet de changer serveur, balancer ou IP sans toucher tous les consommateurs.

---

# 20. Reverse proxy

Exemple conceptuel avec Nginx :

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

Le certificat doit être émis pour le hostname des consommateurs.

---

# 21. Timeouts et indisponibilité

Les consommateurs doivent avoir leur propre timeout pour l’Auth Service.

Ne laissez pas une requête de login bloquée indéfiniment.

Exemple :

~~~text
Connect timeout: 5s
Read timeout:    5s
~~~

Quand l’Auth Service renvoie :

~~~text
503 Service Unavailable
~~~

traitez comme indisponibilité, pas comme mot de passe incorrect.

# 22. Ne pas stocker le mot de passe AD

ERP, firewall, proxy et autres consommateurs ne doivent pas stocker le mot de passe pour le réutiliser.

Flux correct :

~~~text
mot de passe
  |
  v
Auth Service
  |
  v
AD
~~~

Après l’authentification, le consommateur ne garde que la session/identité nécessaire.

---

# 23. Logs

Ne jamais journaliser :

~~~text
password
Authorization: Basic ...
identifiants LDAP
tokens contenant des identifiants
~~~

Peut journaliser, selon l’audit :

~~~text
timestamp
service consommateur
username
résultat
provider
raison technique sans mot de passe
correlation/request id
~~~

Exemple :

~~~text
2026-09-30T15:30:00Z
service=erp
username=euripedes
provider=AD
result=SUCCESS
~~~

---

# 24. Swagger / OpenAPI

L’application expose la doc OpenAPI sur :

~~~text
/v3/api-docs
~~~

et Swagger UI sur :

~~~text
/swagger-ui.html
~~~

Exemple :

~~~text
https://auth.homelab.local/swagger-ui.html
~~~

Utiles en intégration et homologation.

En production, évaluez l’exposition publique et restreignez selon la politique.

---

# 25. Checklist de déploiement

## Auth Service

- [ ] Java installé
- [ ] Maven opérationnel
- [ ] mvn clean test exécuté
- [ ] mvn clean package exécuté
- [ ] JAR généré
- [ ] SERVER_PORT défini si besoin
- [ ] variables LDAP configurées
- [ ] mot de passe LDAP hors Git
- [ ] LDAPS fonctionnel
- [ ] certificat AD de confiance
- [ ] DNS fonctionnel
- [ ] horloge synchronisée
- [ ] firewall restreint aux consommateurs

## Active Directory

- [ ] compte de service créé
- [ ] compte sans privilèges inutiles
- [ ] LDAPS fonctionnel
- [ ] utilisateurs trouvés par la base
- [ ] sAMAccountName fonctionnel
- [ ] attribut memberOf disponible

## ERP

- [ ] URL Auth Service configurée
- [ ] provider AD
- [ ] endpoint d’authentification configuré
- [ ] DTO identité implémenté
- [ ] gestion 401
- [ ] gestion 403
- [ ] gestion 503
- [ ] mapping des groupes AD
- [ ] permissions locales configurées
- [ ] mot de passe non stocké

## Base / SGDB

- [ ] séparer auth applicative et accès base
- [ ] ne pas pointer le SGDB vers une URL HTTP
- [ ] LDAP/AD natif ou adapter/proxy
- [ ] identifiants base séparés des utilisateurs
- [ ] réseau base restreint

## Firewall / Proxy

- [ ] URL Auth Service configurée
- [ ] HTTPS activé
- [ ] provider AD
- [ ] groupes mappés
- [ ] 401 comme échec d’auth
- [ ] 503 comme indisponibilité
- [ ] aucun mot de passe stocké

# 26. Test complet d’homologation

## Test 1 — Service

~~~bash
curl -i https://auth.homelab.local/actuator/health
~~~

Résultat attendu :

~~~text
HTTP/1.1 200
~~~

## Test 2 — Identifiant valide

~~~bash
curl -i \
  -X POST \
  -H 'Content-Type: application/json' \
  -d '{\"username\":\"usuario-teste\",\"password\":\"SENHA\",\"provider\":\"AD\"}' \
  https://auth.homelab.local/api/v1/identity/authenticate
~~~

Résultat attendu :

~~~text
HTTP 200
~~~

## Test 3 — Identifiant invalide

Utilisez un mauvais mot de passe.

Résultat attendu :

~~~text
HTTP 401
~~~

## Test 4 — AD indisponible

Bloquez temporairement l’accès au contrôleur de domaine.

Résultat attendu :

~~~text
HTTP 503
~~~

## Test 5 — Groupe AD

Utilisez un utilisateur de groupes connus.

Confirmez que :

~~~json
"groups": [...]
~~~

contient les groupes attendus.

---

# 27. Modèle d’architecture pour les trois systèmes

Pour ERP, SGDB/services de base et firewall/proxy :

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
        | autre SGDB    |
        +---------------+
~~~

Le point central est :

**L’Auth Service authentifie l’identité ; chaque produit reste responsable de ses données et de son autorisation fonctionnelle.**

---

# 28. Limites du contrat actuel

Le consommateur doit prendre le contrat actuel tel qu’implémenté.

Existant actuellement :

~~~text
POST /api/v1/identity/authenticate
GET  /api/v1/identity/me
GET  /api/v1/identity/groups
GET  /api/v1/identity/provider
GET  /api/v1/identity/authenticated
GET  /actuator/health
~~~

L’authentification HTTP repose sur Basic Authentication.

Le contrat actuel **ne doit pas être documenté comme s’il était déjà OAuth2/OIDC/JWT**.

Si un protocole de token est implémenté à l’avenir, ce sera un nouveau contrat et les consommateurs devront migrer de façon contrôlée.

---

# 29. Résumé pour les équipes de développement

Pour intégrer un nouveau système :

~~~text
1. Configurez AUTH_SERVICE_URL
2. Utilisez HTTPS
3. Appelez POST /api/v1/identity/authenticate
4. Envoyez username/password/provider=AD
5. Validez HTTP 200
6. Lisez identityId, username, provider et groups
7. Créez/retrouvez l’identité locale
8. Appliquez l’autorisation locale
9. Traitez 401 comme échec d’identifiant
10. Traitez 503 comme indisponibilité
11. Ne stockez jamais le mot de passe AD
12. Ne couplez pas le système à la structure interne de l’AD
~~~

C’est le standard d’intégration pour ERP, firewall/proxy et autres services ayant besoin de l’Auth Service comme point central.

---

# 30. Authentification par Bearer Token

Les nouvelles intégrations doivent préférer l’endpoint :

`http
POST /api/v1/identity/token
Content-Type: application/json
`

Payload :

`json
{
  "username": "usuario",
  "password": "senha",
  "provider": "AD"
}
`

Réponse :

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

Le token est signé par l’Auth Service, porte identité et groupes, et est de courte durée. Le consommateur n’a pas besoin de renvoyer le mot de passe AD à chaque appel.

### Configuration obligatoire

Définissez une clé forte et gardez-la hors Git :

`bash
export AUTH_TOKEN_SECRET='une-cle-aleatoire-d-au-moins-32-octets'
export AUTH_TOKEN_ISSUER='auth-service'
export AUTH_TOKEN_TTL_SECONDS='900'
`

Générez une clé adaptée, par exemple :

`bash
openssl rand -base64 48
`

### Utilisation du token

Après obtention du token :

`bash
curl -i \
  -H "Authorization: Bearer <JWT>" \
  https://auth.homelab.local/api/v1/identity/me
`

Les endpoints `/me`, `/groups`, `/provider` et `/authenticated` acceptent Bearer Token. HTTP Basic reste disponible pour les intégrations existantes.

### Sécurité

- Ne stockez pas le mot de passe AD dans l’ERP.
- Ne journalisez ni JWT ni header Authorization.
- Utilisez HTTPS entre consommateurs et Auth Service.
- Gardez le TTL court.
- La même clé de validation doit être protégée comme secret d’infrastructure.
- Pour plusieurs instances, toutes doivent utiliser même clé et issuer.
- Le token représente l’identité/autorisation du moment d’émission ; les changements AD valent avec un nouveau token.

### Compatibilité

`POST /api/v1/identity/authenticate` continue de renvoyer seulement `IdentityDto`, préservant les consommateurs.

Pour les nouvelles intégrations, utilisez `POST /api/v1/identity/token`.
