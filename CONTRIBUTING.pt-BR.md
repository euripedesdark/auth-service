# Contribuindo para o Astral Auth Service

## Código de Conduta
Este projeto e todos os colaboradores são regidos pelo Código de Conduta.

## Configuração de Desenvolvimento

### Pré-requisitos
* Rust 1.80+ (cargo, rustc)
* Node.js 20+ & npm 10+
* OpenSSL / PAM development libraries

### Compilação e Execução

```bash
# Backend Rust
cargo test
cargo build --release

# Frontend React
cd web
npm install
npm run build
```

## Estrutura do Projeto

```
astral-auth-service/
├── Cargo.toml                 # Definição de dependências Rust (Axum, Tokio, JsonWebToken)
├── Cargo.lock                 # Versões travadas de dependências
├── src/                       # Código-fonte do serviço de autenticação
│   ├── main.rs                # Ponto de entrada do serviço e carregamento de configurações
│   ├── server.rs              # Inicialização do servidor Axum e roteador REST
│   ├── config/                # Módulos de configuração de ambiente e JWT
│   ├── controller/            # Controladores da API (login, tokens, validação, providers)
│   ├── domain/                # Modelos de domínio (usuário, credenciais, claims)
│   └── service/               # Lógica de negócio:
│       ├── linux_privilege_service.rs # PAM real, verificação de wheel/sudo e isolamento Postgres
│       ├── token_service.rs   # Emissão e validação de tokens JWT / SSO
│       └── ldap_service.rs    # Integração com Samba Active Directory
├── web/                       # Frontend SPA React 18 + Vite + Tailwind CSS
│   ├── src/                   # Telas de login unificado, componentes e modais
│   ├── package.json
│   ├── vite.config.js
│   └── dist/                  # Build de produção compilado
├── docs/                      # Documentação técnica e guias de integração
└── .github/                   # Workflows de CI/CD
```

## Diretrizes para Pull Requests
1. Crie uma branch descritiva a partir de main.
2. Escreva testes unitários para novas regras de autenticação e validação.
3. Garanta que npm run build e cargo test passem sem erros.
4. Submeta o Pull Request com descrição detalhada.
