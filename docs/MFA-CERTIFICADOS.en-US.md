# Auth Service — MFA and Certificates (Usage Guide)

Digital certificate delivery bound to AD identities, gated by MFA TOTP.
No database in the core: MFA secrets in memory, certificates on disk.

## 1. Admin flow (`/auth/admin/`)

1. Open `https://<HOST>/auth/admin/` (or `https://<HOST>/auth`).
2. Log in with your **domain user** and password.
3. **Issue certificate:** type the `AD username` → **Issue**.
   Generates an RSA-2048 keypair + CSR (PKCS#10) and marks it
   `PENDENTE_DOWNLOAD`. Only works for users that exist in AD.
4. **Create MFA:** type the user → **Generate QR Code**.
   - QR valid for **5 minutes**, **single use**.
   - A new code **replaces** the previous one for the same user.
   - Click **Save QR** to download the PNG and send it by email/WhatsApp.
   - **30-second** codes, compatible with Google/Microsoft Authenticator, Aegis, Authy.
5. **Pending:** lists certificates waiting for download.

## 2. User flow (`/mfa/` on port 80 or `/auth/mfa/`)

1. Open `http://<HOST>/mfa/` (local network) or `https://<HOST>/auth/mfa/`.
2. Type your **username** → **Next**.
3. Type the **6-digit code** from the app → **Validate and download**.
4. `certificado.zip` downloads automatically
   (`.key.pem` key + `.req.csr` request + `README`).
5. Each code and each download link works **only once**.

## 3. Endpoints

| Method | Route | Auth | Description |
|--------|-------|------|-------------|
| POST | `/api/v1/identity/authenticate` | — | AD login (`username`, `password`, `provider: "AD"`) |
| POST | `/api/v1/mfa/setup` | admin | Creates MFA+QR (`username`), 5 min |
| GET | `/api/v1/mfa/qr/{id}.png` | — | QR PNG (5 min) |
| POST | `/api/v1/mfa/verify` | — | Validates `{username, code}` → `mfaId` |
| POST | `/api/v1/certificates/issue` | admin | Issues for an AD user |
| GET | `/api/v1/certificates/pending` | admin | Lists pending |
| POST | `/api/v1/certificates/download-token` | mfaId | Exchanges MFA for a single-use token (5 min) |
| GET | `/api/v1/certificates/download?token=` | token | Downloads the ZIP (single use) |

## 4. Security rules

- Zero Trust: downloads require valid MFA + pending certificate + active AD account.
- QR expires in 5 min; codes rotate every 30 s (±2 steps tolerance).
- Everything is audited (login, MFA, issuance, download).
- Credentials via vault/environment (`<HOST>`, `<USUARIO>`, `<SENHA>`, `<TOKEN>`) — never in files or commits.
