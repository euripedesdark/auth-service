# Contribuer à Astral Auth Service

## Code de Conduite
Ce projet et l’ensemble des contributeurs sont régis par le Code de Conduite.

## Configuration de Développement

### Prérequis
* Rust 1.80+ (cargo, rustc)
* Node.js 20+ & npm 10+
* OpenSSL / PAM development libraries

### Compilation et Exécution

```bash
# Backend Rust
cargo test
cargo build --release

# Frontend React
cd web
npm install
npm run build
```

## Structure du Projet

```
astral-auth-service/
├── Cargo.toml                 # Métadonnées et dépendances Rust (Axum, Tokio, JsonWebToken)
├── Cargo.lock                 # Versions verrouillées des dépendances
├── src/                       # Code source du service d'authentification
│   ├── main.rs                # Point d'entrée et initialisation de configuration
│   ├── server.rs              # Serveur HTTP Axum et routeur REST
│   ├── config/                # Configuration d'environnement et JWT
│   ├── controller/            # Contrôleurs API (connexion, jetons, validation)
│   ├── domain/                # Modèles de domaine (utilisateur, identifiants, claims)
│   └── service/               # Logique métier :
│       ├── linux_privilege_service.rs # PAM réel, validation wheel/sudo et isolement Postgres
│       ├── token_service.rs   # Émission et vérification des jetons JWT / SSO
│       └── ldap_service.rs    # Intégration Samba Active Directory LDAP
├── web/                       # Frontend SPA React 18 + Vite + Tailwind CSS
│   ├── src/                   # Interfaces de connexion et modaux
│   ├── package.json
│   ├── vite.config.js
│   └── dist/                  # Artefacts de production compilés
├── docs/                      # Documentation technique
└── .github/                   # Pipelines CI/CD
```

## Lignes Directrices pour les Pull Requests
1. Créez une branche descriptive à partir de main.
2. Ajoutez des tests unitaires pour les fonctionnalités d’authentification.
3. Assurez-vous que npm run build et cargo test réussissent sans erreur.
4. Soumettez la Pull Request avec un résumé clair.
