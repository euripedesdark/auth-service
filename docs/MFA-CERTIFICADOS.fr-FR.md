# Auth Service — MFA et Certificats (Guide d'utilisation)

Distribution de certificats numériques liés aux identités AD, protégée par MFA TOTP.
Sans base de données au cœur : secrets MFA en mémoire, certificats sur disque.

## 1. Parcours administrateur (`/auth/admin/`)

1. Ouvrez `https://<HOST>/auth/admin/` (ou `https://<HOST>/auth`).
2. Connectez-vous avec votre **utilisateur du domaine** et mot de passe.
3. **Émettre un certificat :** saisissez le `nom d'utilisateur AD` → **Émettre**.
   Génère une paire de clés RSA-2048 + CSR (PKCS#10) et la marque
   `PENDENTE_DOWNLOAD`. Fonctionne uniquement pour un utilisateur AD existant.
4. **Créer un MFA :** saisissez l'utilisateur → **Générer le QR**.
   - QR valable **5 minutes**, **usage unique**.
   - Un nouveau code **remplace** le précédent du même utilisateur.
   - Cliquez **Enregistrer le QR** pour télécharger le PNG et l'envoyer par e-mail/WhatsApp.
   - Codes de **30 secondes**, compatibles Google/Microsoft Authenticator, Aegis, Authy.
5. **En attente :** liste les certificats en attente de téléchargement.

## 2. Parcours utilisateur (`/mfa/` sur le port 80 ou `/auth/mfa/`)

1. Ouvrez `http://<HOST>/mfa/` (réseau local) ou `https://<HOST>/auth/mfa/`.
2. Saisissez votre **utilisateur** → **Suivant**.
3. Saisissez le **code à 6 chiffres** de l'application → **Valider et télécharger**.
4. `certificado.zip` se télécharge automatiquement
   (clé `.key.pem` + demande `.req.csr` + `LEIA-ME`).
5. Chaque code et chaque lien ne valent **qu'une seule fois**.

## 3. Endpoints

| Méthode | Route | Auth | Description |
|---------|-------|------|-------------|
| POST | `/api/v1/identity/authenticate` | — | Login AD (`username`, `password`, `provider: "AD"`) |
| POST | `/api/v1/mfa/setup` | admin | Crée MFA+QR (`username`), 5 min |
| GET | `/api/v1/mfa/qr/{id}.png` | — | PNG du QR (5 min) |
| POST | `/api/v1/mfa/verify` | — | Valide `{username, code}` → `mfaId` |
| POST | `/api/v1/certificates/issue` | admin | Émet pour un utilisateur AD |
| GET | `/api/v1/certificates/pending` | admin | Liste les certificats en attente |
| POST | `/api/v1/certificates/download-token` | mfaId | Échange le MFA contre un jeton unique (5 min) |
| GET | `/api/v1/certificates/download?token=` | token | Télécharge le ZIP (usage unique) |

## 4. Règles de sécurité

- Zero Trust : le téléchargement exige MFA valide + certificat en attente + compte AD actif.
- Le QR expire en 5 min ; les codes tournent toutes les 30 s (tolérance ±2 pas).
- Tout est audité (login, MFA, émission, téléchargement).
- Identifiants via coffre/environnement (`<HOST>`, `<USUARIO>`, `<SENHA>`, `<TOKEN>`) — jamais dans les fichiers ni les commits.
