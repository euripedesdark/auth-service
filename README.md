# Astral Auth Service

🇧🇷 **Português (Brasil):** Clique no link abaixo para ler o README completo em português: [README.pt-BR.md](README.pt-BR.md)

🇺🇸 **English:** Click the link below to read the complete README in English: [README.en-US.md](README.en-US.md)

🇪🇸 **Español:** Haz clic en el siguiente enlace para leer el README completo en español: [README.es-ES.md](README.es-ES.md)

🇫🇷 **Français :** Cliquez sur le lien ci-dessous pour lire le README complet en français : [README.fr-FR.md](README.fr-FR.md)

## OAuth2 Authorization Code + PKCE

O auth-service inclui autorizacao por codigo com PKCE S256, login corporativo
via LDAPS, troca de codigo, userinfo e sessao SSO. O HCI cadastra e implanta
os dois servicos com `configure_oauth2.py`.

[Configuracao, endpoints e deploy OAuth2](docs/oauth2.md).


## Atualizar backend e frontend

Na primeira vez, atualize o script no checkout do **auth-service**:

```bash
git pull --ff-only origin main
python3 deploy_auth.py --install-deps
```

Depois, basta executar `python3 deploy_auth.py`. Ele faz `git pull --ff-only
origin main`, recarrega o script atualizado, testa/compila o Rust e executa
`npm ci` e `npm run build` em `web`. Use Node.js 22.12+ ou 24 e Rust stable.
`--install-deps` instala dependencias com dnf/yum no Fedora/RHEL ou apt no
Debian/Ubuntu. O script solicita sudo e usa o usuario original para Git e builds.

O servico continua ativo durante os builds. So depois ele para a unit existente,
publica backend e frontend na mesma release e inicia o servico. A verificacao
confere health, metadados OAuth2/PKCE e o HTML em `/admin/`; se a ativacao falhar,
restaura a unit e a release anteriores. O frontend tambem e servido em `/mfa/`.
Preserva `auth.env`, `oauth2.env`, porta 8181 e configuracao nginx/TLS.
`--no-pull` permite implantar o checkout atual, por exemplo em CI.
Este comando atualiza o auth-service; nao provisiona Samba nem altera o HCI.
