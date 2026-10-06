# Auth Service — MFA y Certificados (Guía de uso)

Entrega de certificados digitales ligados a identidades de AD, protegida por MFA TOTP.
Sin base de datos en el núcleo: secretos MFA en memoria, certificados en disco.

## 1. Flujo del administrador (`/auth/admin/`)

1. Abra `https://<HOST>/auth/admin/` (o `https://<HOST>/auth`).
2. Inicie sesión con su **usuario del dominio** y contraseña.
3. **Emitir certificado:** escriba el `usuario de AD` → **Emitir**.
   Genera par de claves RSA-2048 + CSR (PKCS#10) y lo marca
   `PENDENTE_DOWNLOAD`. Solo funciona para usuarios que existen en AD.
4. **Crear MFA:** escriba el usuario → **Generar QR**.
   - QR válido por **5 minutos**, **un solo uso**.
   - Un código nuevo **reemplaza** al anterior del mismo usuario.
   - Clic en **Guardar QR** para descargar el PNG y enviarlo por correo/WhatsApp.
   - Códigos de **30 segundos**, compatibles con Google/Microsoft Authenticator, Aegis, Authy.
5. **Pendientes:** lista los certificados esperando descarga.
6. **Revocar:** escriba el usuario → **Revocar** (marca `REVOGADO`, bloquea la descarga).

## 2. Flujo del usuario (`/mfa/` en puerto 80 o `/auth/mfa/`)

1. Abra `http://<HOST>/mfa/` (red local) o `https://<HOST>/auth/mfa/`.
2. Escriba su **usuario** → **Siguiente**.
3. Escriba el **código de 6 dígitos** de la app → **Validar y descargar**.
4. `certificado.zip` se descarga automáticamente
   (clave `.key.pem` + solicitud `.req.csr` + certificado `.crt` para Windows + `LEIA-ME`).
5. Cada código y cada enlace valen **una sola vez**.

## 3. Endpoints

| Método | Ruta | Auth | Descripción |
|--------|------|------|-------------|
| POST | `/api/v1/identity/authenticate` | — | Login AD (`username`, `password`, `provider: "AD"`) |
| POST | `/api/v1/mfa/setup` | admin | Crea MFA+QR (`username`), 5 min |
| GET | `/api/v1/mfa/qr/{id}.png` | — | PNG del QR (5 min) |
| POST | `/api/v1/mfa/verify` | — | Valida `{username, code}` → `mfaId` |
| POST | `/api/v1/certificates/issue` | admin | Emite para un usuario AD |
| GET | `/api/v1/certificates/pending` | admin | Lista pendientes |
| POST | `/api/v1/certificates/revoke` | admin | Revoca todo del usuario |
| GET | `/api/v1/certificates/revoked` | admin | Lista revocados |
| POST | `/api/v1/certificates/download-token` | mfaId | Canjea MFA por token único (5 min) |
| GET | `/api/v1/certificates/download?token=` | token | Descarga el ZIP (un solo uso) |

## 4. Reglas de seguridad

- Zero Trust: la descarga exige MFA válido + certificado pendiente + cuenta AD activa.
- El QR expira en 5 min; los códigos rotan cada 30 s (tolerancia ±2 pasos).
- Todo se audita (login, MFA, emisión, descarga).
- Credenciales por vault/entorno (`<HOST>`, `<USUARIO>`, `<SENHA>`, `<TOKEN>`) — nunca en archivos ni commits.
