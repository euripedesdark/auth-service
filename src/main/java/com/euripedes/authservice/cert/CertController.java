package com.euripedes.authservice.cert;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/certificates")
@Tag(name = "Certificates")
public class CertController {
    private final CertIssueService service;
    private final com.euripedes.authservice.mfa.MfaService mfa;

    public CertController(CertIssueService service,
                          com.euripedes.authservice.mfa.MfaService mfa) {
        this.service = service;
        this.mfa = mfa;
    }

    public record IssueRequest(@NotBlank String username) {}

    @PostMapping("/issue")
    @Operation(summary = "Emite chave+CSR para usuário AD e marca para download (admin)")
    public ResponseEntity<?> issue(Authentication admin, @Valid @RequestBody IssueRequest req) throws Exception {
        try {
            var issued = service.issue(admin.getName(), req.username());
            return ResponseEntity.ok(issued);
        } catch (Exception e) {
            return ResponseEntity.status(404).body(Map.of("error", "Usuario AD nao encontrado"));
        }
    }

    @GetMapping("/pending")
    @Operation(summary = "Lista certificados marcados para download (admin)")
    public List<CertIssueService.Issued> pending() throws Exception {
        return service.pending();
    }

    @PostMapping("/revoke")
    @Operation(summary = "Revoga certificados do usuário (admin)")
    public List<CertIssueService.Issued> revoke(Authentication admin,
                                                @Valid @RequestBody IssueRequest req) {
        return service.revoke(admin.getName(), req.username());
    }

    @GetMapping("/revoked")
    @Operation(summary = "Lista certificados revogados (admin)")
    public List<CertIssueService.Issued> revoked() throws Exception {
        return service.revoked();
    }

    @PostMapping("/download-token")
    @Operation(summary = "Troca MFA válido por token único de download")
    public ResponseEntity<?> downloadToken(@Valid @RequestBody TokenRequest req) throws Exception {
        if (!mfa.takeValidated(req.username().trim(), req.mfaId().trim())) {
            return ResponseEntity.status(401).body(Map.of("error", "MFA invalido ou ja usado"));
        }
        String t = service.createDownloadToken(req.username().trim());
        if (t == null) return ResponseEntity.status(404)
            .body(Map.of("error", "Nenhum certificado pendente para o usuario"));
        return ResponseEntity.ok(Map.of("downloadToken", t, "validitySeconds", 300));
    }

    public record TokenRequest(@NotBlank String username, @NotBlank String mfaId) {}

    @GetMapping("/download")
    @Operation(summary = "Baixa o ZIP do certificado (token único, público)")
    public ResponseEntity<byte[]> download(@RequestParam String token) throws Exception {
        byte[] zip = service.download(token);
        if (zip == null) return ResponseEntity.status(410).build();
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"certificado.zip\"")
            .contentType(MediaType.parseMediaType("application/zip"))
            .body(zip);
    }
}
