# Contribuyendo a Astral Auth Service

## Código de Conducta
Este proyecto y sus participantes se rigen por el Código de Conducta.

## Configuración de Desarrollo

### Requisitos Previos
* Rust 1.80+ (cargo, rustc)
* Node.js 20+ & npm 10+
* OpenSSL / PAM development libraries

### Compilación y Ejecución

```bash
# Backend Rust
cargo test
cargo build --release

# Frontend React
cd web
npm install
npm run build
```

## Estructura del Proyecto

```
astral-auth-service/
├── Cargo.toml                 # Metadatos y dependencias Rust (Axum, Tokio, JsonWebToken)
├── Cargo.lock                 # Versiones bloqueadas de dependencias
├── src/                       # Código fuente del servicio de autenticación
│   ├── main.rs                # Punto de entrada y carga de configuración
│   ├── server.rs              # Servidor Axum y enrutador REST
│   ├── config/                # Módulos de configuración de entorno y JWT
│   ├── controller/            # Controladores API (inicio de sesión, tokens, validación)
│   ├── domain/                # Modelos de dominio (usuario, credenciales, claims)
│   └── service/               # Lógica de negocio:
│       ├── linux_privilege_service.rs # PAM real, verificación wheel/sudo y aislamiento Postgres
│       ├── token_service.rs   # Emisión y verificación de tokens JWT / SSO
│       └── ldap_service.rs    # Integración con Samba Active Directory LDAP
├── web/                       # Frontend SPA React 18 + Vite + Tailwind CSS
│   ├── src/                   # Pantallas de autenticación y modales
│   ├── package.json
│   ├── vite.config.js
│   └── dist/                  # Compilación estática de producción
├── docs/                      # Documentación técnica
└── .github/                   # Flujos de CI/CD
```

## Directrices para Pull Requests
1. Cree una rama descriptiva a partir de main.
2. Añada pruebas unitarias para nuevas funciones de autenticación.
3. Compruebe que npm run build y cargo test se ejecuten sin errores.
4. Envíe el Pull Request con documentación de cambios.
