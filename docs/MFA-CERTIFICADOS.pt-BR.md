# Auth Service — MFA e Certificados (Guia de Uso)

Entrega de certificados digitais por identidade do AD, com MFA TOTP.
Sem banco de dados no núcleo: segredos MFA em memória, certificados em arquivos.

## 1. Fluxo do administrador (`/auth/admin/`)

1. Abra `https://<HOST>/auth/admin/` (ou `https://<HOST>/auth`).
2. Faça login com seu **usuário do domínio** e senha.
3. **Emitir certificado:** digite o `nome do usuário AD` → **Emitir**.
   Gera par de chaves RSA-2048 + CSR (PKCS#10) e marca como
   `PENDENTE_DOWNLOAD`. Só funciona para usuário que existe no AD.
4. **Criar MFA:** digite o usuário → **Gerar QR Code**.
   - QR válido por **5 minutos**, **uso único**.
   - Um código novo **substitui** o anterior do mesmo usuário.
   - Clique em **Salvar QR** para baixar o PNG e enviar por e-mail/WhatsApp.
   - Códigos de **30 segundos**, compatíveis com Google/Microsoft Authenticator, Aegis, Authy.
5. **Pendentes:** lista certificados aguardando download.
6. **Revogar:** digite o usuário → **Revogar** (marca `REVOGADO`, bloqueia download).

## 2. Fluxo do usuário (`/mfa/` na porta 80 ou `/auth/mfa/`)

1. Abra `http://<HOST>/mfa/` (rede local) ou `https://<HOST>/auth/mfa/`.
2. Digite seu **usuário** → **Avançar**.
3. Digite o **código de 6 dígitos** do aplicativo → **Validar e baixar**.
4. O arquivo `certificado.zip` baixa automaticamente
   (`chave .key.pem` + `pedido .req.csr` + `certificado .crt` para Windows + `LEIA-ME.txt`).
5. Cada código e cada link de download valem **uma única vez**.

## 3. Endpoints

| Método | Rota | Auth | Descrição |
|--------|------|------|-----------|
| POST | `/api/v1/identity/authenticate` | — | Login AD (`username`, `password`, `provider: "AD"`) |
| POST | `/api/v1/mfa/setup` | admin | Cria MFA+QR (`username`), 5 min |
| GET | `/api/v1/mfa/qr/{id}.png` | — | PNG do QR (5 min) |
| POST | `/api/v1/mfa/verify` | — | Valida `{username, code}` → `mfaId` |
| POST | `/api/v1/certificates/issue` | admin | Emite para usuário AD |
| GET | `/api/v1/certificates/pending` | admin | Lista pendentes |
| POST | `/api/v1/certificates/revoke` | admin | Revoga tudo do usuário |
| GET | `/api/v1/certificates/revoked` | admin | Lista revogados |
| POST | `/api/v1/certificates/download-token` | mfaId | Troca MFA por token único (5 min) |
| GET | `/api/v1/certificates/download?token=` | token | Baixa o ZIP (uso único) |

## 4. Regras de segurança

- Zero Trust: download exige MFA válido + certificado pendente + conta AD ativa.
- QR expira em 5 min; código gira a cada 30 s (tolerância ±2 passos).
- Tudo é auditado (login, MFA, emissão, download).
- Credenciais em vault/variáveis (`<HOST>`, `<USUARIO>`, `<SENHA>`, `<TOKEN>`) — nunca em arquivos ou commits.
