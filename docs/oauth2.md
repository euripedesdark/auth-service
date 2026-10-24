# OAuth2 do auth-service

O servidor oferece Authorization Code com PKCE S256 e clientes confidenciais
cadastrados pelo administrador em JSON. A configuracao e o deploy integrados
estao no [guia do HCI](https://github.com/euripedesdark/astral-platform-hci/blob/main/docs/oauth2.md).

No checkout do HCI:

```bash
python3 configure_oauth2.py --public-url https://192.168.2.10 --install-deps
```

`AUTH_OAUTH_CLIENTS_FILE` aponta para uma lista JSON de objetos com `client_id`,
`client_secret` (>=32 caracteres) e `redirect_uris` exatos HTTPS. O configurador
gera o segredo e preserva outros clientes. Nao ha cadastro publico de clientes.
`AUTH_OAUTH_ISSUER_URL` indica a origem/caminho publico, por exemplo
`https://192.168.2.10/auth`, que o nginx encaminha a este servico.

| Endpoint interno | Funcao |
|---|---|
| `GET /oauth2/authorize` | Valida cliente, callback e PKCE; abre login ou reutiliza sessao do provedor |
| `POST /oauth2/authorize` | Valida transacao e cookie, confere senha no LDAPS e emite codigo |
| `POST /oauth2/token` | Troca codigo de uso unico por token, usando `client_secret_post` e code verifier |
| `GET /oauth2/userinfo` | Valida access token e retorna identidade/grupos do escopo autorizado |
| `POST /oauth2/logout` | Encerra a sessao do provedor |
| `GET /.well-known/oauth-authorization-server` | Metadata RFC 8414 |

As APIs `/api/v1/identity/*` existentes permanecem disponiveis. O novo fluxo
usa tokens de acesso opacos (15 minutos), codigos de 60 segundos e sessao do
provedor de oito horas, em memoria. Reiniciar o processo invalida os logins e
tokens OAuth pendentes. Os scopes disponiveis sao `profile groups`.

`AUTH_LDAP_CA_FILE` adiciona a CA PEM do AD quando ela nao esta no trust store.
A verificacao TLS e de hostname fica habilitada. Usuario/senha do AD sao
conferidos por bind com verificacao do codigo LDAP; o filtro e escapado.
A identidade utiliza objectGUID, com fallback objectSid/UPN, sem trocar por
sAMAccountName quando a conta e renomeada.

Depois do cadastro pelo configurador, `python3 deploy_auth.py` recompila e
reimplanta apenas este servico, preservando auth.env e recuperando a unit/release
anteriores em falha. Nao modifica nginx, TLS, banco nem o ERP.

Testes: `cargo test --locked` e
`python3 -m unittest discover -s tests -v`. O HCI inclui
`scripts/test-oauth-e2e.py`, que executa os dois binarios com LDAPS descartavel.
