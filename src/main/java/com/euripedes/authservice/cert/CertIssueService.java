package com.euripedes.authservice.cert;

import com.euripedes.authservice.audit.AuditService;
import com.euripedes.authservice.provider.AdProvider;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import javax.security.auth.x500.X500Principal;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.pkcs.jcajce.JcaPKCS10CertificationRequestBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Emissão de material de certificado por usuário do AD.
 * Sem banco e sem chave da CA aqui: gera par de chaves + CSR (PKCS#10)
 * e marca para download. O download é liberado pelo MFA (token único).
 */
@Service
public class CertIssueService {

    public record Issued(String id, String username, String dn, String status,
                         long createdAt, String createdBy) {}

    private record DlToken(String certId, long expiresAt) {}

    private final Path baseDir;
    private final AdProvider adProvider;
    private final AuditService audit;
    private final Map<String, DlToken> tokens = new ConcurrentHashMap<>();

    public CertIssueService(@Value("${auth.pki.user-cert-dir:user-certificados}") String dir,
                            AdProvider adProvider, AuditService audit) throws Exception {
        this.baseDir = Path.of(dir);
        Files.createDirectories(baseDir);
        this.adProvider = adProvider;
        this.audit = audit;
    }

    public Issued issue(String createdBy, String username) throws Exception {
        String clean = username.trim();
        // Só emite para identidade real do AD (resolve falha se não existir).
        var identity = adProvider.resolve(clean);
        String id = UUID.randomUUID().toString().substring(0, 8);
        Path dir = baseDir.resolve(safe(clean));
        Files.createDirectories(dir);

        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        KeyPair kp = gen.generateKeyPair();

        var csrBuilder = new JcaPKCS10CertificationRequestBuilder(
            new X500Principal("CN=" + clean), kp.getPublic());
        var signer = new JcaContentSignerBuilder("SHA256withRSA").build(kp.getPrivate());
        var csr = csrBuilder.build(signer);

        Files.writeString(dir.resolve(id + ".key.pem"), toPem(kp.getPrivate()));
        Files.writeString(dir.resolve(id + ".req.csr"), toPem(csr));
        long now = Instant.now().getEpochSecond();
        String meta = "{\"id\":\"" + id + "\",\"username\":\"" + clean + "\",\"dn\":\""
            + identity.identityId() + "\",\"status\":\"PENDENTE_DOWNLOAD\",\"createdAt\":" + now
            + ",\"createdBy\":\"" + createdBy + "\"}";
        Files.writeString(dir.resolve(id + ".meta.json"), meta, StandardCharsets.UTF_8);
        audit.authenticationSuccess(clean, "CERT_ISSUED");
        return new Issued(id, clean, identity.identityId(), "PENDENTE_DOWNLOAD", now, createdBy);
    }

    public List<Issued> pending() throws Exception {
        List<Issued> out = new ArrayList<>();
        if (!Files.exists(baseDir)) return out;
        try (var users = Files.list(baseDir)) {
            for (Path u : users.toList()) {
                if (!Files.isDirectory(u)) continue;
                try (var files = Files.list(u)) {
                    for (Path m : files.filter(f -> f.toString().endsWith(".meta.json")).toList()) {
                        String j = Files.readString(m, StandardCharsets.UTF_8);
                        if (j.contains("PENDENTE_DOWNLOAD")) out.add(parseMeta(j));
                    }
                }
            }
        }
        return out;
    }

    /** Cria token único de download após MFA válido (5 min). */
    public String createDownloadToken(String username) throws Exception {
        for (Issued i : pending()) {
            if (i.username().equalsIgnoreCase(username)) {
                String t = UUID.randomUUID().toString();
                tokens.put(t, new DlToken(i.id(), Instant.now().getEpochSecond() + 300));
                return t;
            }
        }
        return null;
    }

    /** Consome o token e devolve o ZIP (chave + CSR + meta). Uso único. */
    public byte[] download(String token) throws Exception {
        DlToken dt = tokens.remove(token);
        if (dt == null || dt.expiresAt() < Instant.now().getEpochSecond()) return null;
        Path meta = findMeta(dt.certId());
        if (meta == null) return null;
        Path dir = meta.getParent();
        String user = dir.getFileName().toString();
        var zip = new java.io.ByteArrayOutputStream();
        try (var z = new ZipOutputStream(zip, StandardCharsets.UTF_8)) {
            addFile(z, dir.resolve(dt.certId() + ".key.pem"), user + ".key.pem");
            addFile(z, dir.resolve(dt.certId() + ".req.csr"), user + ".req.csr");
            addText(z, "LEIA-ME.txt", "Certificado de " + user + ".\n"
                + "Assine o CSR na CA e devolva o certificado ao usuario.\n");
        }
        String j = Files.readString(meta, StandardCharsets.UTF_8)
            .replace("PENDENTE_DOWNLOAD", "BAIXADO");
        Files.writeString(meta, j, StandardCharsets.UTF_8);
        audit.authenticationSuccess(user, "CERT_DOWNLOAD");
        return zip.toByteArray();
    }

    private Path findMeta(String certId) throws Exception {
        try (var users = Files.list(baseDir)) {
            for (Path u : users.toList()) {
                Path m = u.resolve(certId + ".meta.json");
                if (Files.exists(m)) return m;
            }
        }
        return null;
    }

    private void addFile(ZipOutputStream z, Path f, String name) throws Exception {
        z.putNextEntry(new ZipEntry(name));
        z.write(Files.readAllBytes(f));
        z.closeEntry();
    }

    private void addText(ZipOutputStream z, String name, String text) throws Exception {
        z.putNextEntry(new ZipEntry(name));
        z.write(text.getBytes(StandardCharsets.UTF_8));
        z.closeEntry();
    }

    private Issued parseMeta(String j) {
        return new Issued(str(j, "id"), str(j, "username"), str(j, "dn"),
            str(j, "status"), Long.parseLong(num(j, "createdAt")), str(j, "createdBy"));
    }

    private String str(String j, String k) {
        String p = "\"" + k + "\":\"";
        int i = j.indexOf(p) + p.length();
        return j.substring(i, j.indexOf('"', i));
    }

    private String num(String j, String k) {
        String p = "\"" + k + "\":";
        int i = j.indexOf(p) + p.length();
        int e = i;
        while (e < j.length() && (Character.isDigit(j.charAt(e)))) e++;
        return j.substring(i, e);
    }

    private String safe(String u) {
        return u.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private String toPem(Object o) throws Exception {
        StringWriter sw = new StringWriter();
        try (JcaPEMWriter w = new JcaPEMWriter(sw)) {
            w.writeObject(o);
        }
        return sw.toString();
    }
}
