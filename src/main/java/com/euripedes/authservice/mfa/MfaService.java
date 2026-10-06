package com.euripedes.authservice.mfa;

import com.euripedes.authservice.audit.AuditService;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.qrcode.QRCodeWriter;
import java.io.ByteArrayOutputStream;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

/**
 * MFA TOTP com validade curta (padrão 300s), uso único.
 * Sem banco: segredos em memória, indexados por usuário.
 */
@Service
public class MfaService {
    /** Passo do TOTP: 30s (padrão Google/Microsoft Authenticator). */
    public static final long STEP_SECONDS = 30L;
    /** Validade do QR/setup pendente: 5 min. */
    public static final long RECORD_TTL_SECONDS = 300L;

    public record Setup(String id, String username, String secret, String otpauthUrl,
                        String qrDataUrl, long expiresAt) {}
    private record Record(String id, String username, byte[] secret, long expiresAt) {}

    private final Map<String, Record> byId = new ConcurrentHashMap<>();
    private final Map<String, Record> consumed = new ConcurrentHashMap<>();
    private final AuditService audit;
    private final SecureRandom random = new SecureRandom();

    public MfaService(AuditService audit) {
        this.audit = audit;
    }

    public Setup setup(String username) {
        purge();
        // Novo código substitui o antigo: sem acúmulo de chaves por usuário.
        String clean = username.trim();
        byId.entrySet().removeIf(e -> e.getValue().username().equalsIgnoreCase(clean));
        consumed.entrySet().removeIf(e -> e.getValue().username().equalsIgnoreCase(clean));
        byte[] secret = new byte[20];
        random.nextBytes(secret);
        String b32 = Base32.encode(secret);
        String url = otpauthUrl(clean, b32);
        String id = UUID.randomUUID().toString();
        long exp = Instant.now().getEpochSecond() + RECORD_TTL_SECONDS;
        byId.put(id, new Record(id, clean, secret, exp));
        audit.authenticationSuccess(clean, "MFA_SETUP");
        return new Setup(id, clean, b32, url, "data:image/png;base64," + qrPngBase64(url), exp);
    }

    private static String otpauthUrl(String username, String b32) {
        return "otpauth://totp/BrasilCloud:" + username + "?secret=" + b32
            + "&issuer=BrasilCloud&period=" + STEP_SECONDS + "&digits=6&algorithm=SHA1";
    }

    public byte[] qrPng(String id) {
        Record r = valid(id);
        if (r == null) return null;
        return qrPngBytes(otpauthUrl(r.username(), Base32.encode(r.secret())));
    }

    /** Valida o código; em caso de sucesso consome o registro (uso único). */
    public String consume(String username, String code) {
        purge();
        for (Record r : byId.values()) {
            if (r.username().equalsIgnoreCase(username)
                    && Totp.verify(r.secret(), code, STEP_SECONDS, 2)) {
                byId.remove(r.id());
                consumed.put(r.id(), r);
                audit.authenticationSuccess(username, "MFA_VERIFY");
                return r.id();
            }
        }
        audit.authenticationFailure("MFA_VERIFY");
        return null;
    }

    /** Confirma e queima o MFA validado (liga verify -> download). Uso único. */
    public boolean takeValidated(String username, String mfaId) {
        purge();
        Record r = consumed.remove(mfaId);
        return r != null && r.username().equalsIgnoreCase(username);
    }

    private Record valid(String id) {
        purge();
        Record r = byId.get(id);
        if (r == null) return null;
        if (r.expiresAt() < Instant.now().getEpochSecond()) {
            byId.remove(id);
            return null;
        }
        return r;
    }

    private void purge() {
        long now = Instant.now().getEpochSecond();
        byId.entrySet().removeIf(e -> e.getValue().expiresAt() < now);
        consumed.entrySet().removeIf(e -> e.getValue().expiresAt() < now);
    }

    private String qrPngBase64(String text) {
        return Base64.getEncoder().encodeToString(qrPngBytes(text));
    }

    private byte[] qrPngBytes(String text) {
        try {
            var matrix = new QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 300, 300);
            var out = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(matrix, "PNG", out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao gerar QR", e);
        }
    }
}
