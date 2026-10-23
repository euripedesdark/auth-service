# Contributing to Astral Auth Service

## Code of Conduct
This project and all contributors are governed by the Code of Conduct.

## Development Setup

### Prerequisites
* Rust 1.80+ (cargo, rustc)
* Node.js 20+ & npm 10+
* OpenSSL / PAM development libraries

### Build and Execution

```bash
# Backend Rust
cargo test
cargo build --release

# Frontend React
cd web
npm install
npm run build
```

## Project Structure

```
astral-auth-service/
├── Cargo.toml                 # Rust dependencies and metadata (Axum, Tokio, JsonWebToken)
├── Cargo.lock                 # Locked dependency versions
├── src/                       # Authentication service source code
│   ├── main.rs                # Service entrypoint and configuration bootstrap
│   ├── server.rs              # Axum HTTP server and REST router initialization
│   ├── config/                # Environment and JWT configuration modules
│   ├── controller/            # API controllers (login, tokens, validation, providers)
│   ├── domain/                # Domain models (user, credentials, claims)
│   └── service/               # Business logic:
│       ├── linux_privilege_service.rs # Real PAM, wheel/sudo validation & Postgres superuser denial
│       ├── token_service.rs   # JWT issuance and SSO verification
│       └── ldap_service.rs    # Samba Active Directory LDAP/Kerberos integration
├── web/                       # React 18 SPA Frontend + Vite + Tailwind CSS
│   ├── src/                   # Unified login screens, components and modals
│   ├── package.json
│   ├── vite.config.js
│   └── dist/                  # Compiled production build
├── docs/                      # Technical documentation and integration guides
└── .github/                   # CI/CD workflows
```

## Pull Request Guidelines
1. Create a descriptive feature branch from main.
2. Write unit tests for new authentication mechanisms.
3. Ensure npm run build and cargo test complete cleanly.
4. Submit a Pull Request with a clear summary.
