package com.euripedes.authservice.mfa;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/mfa")
@Tag(name = "MFA")
public class MfaController {
    private final MfaService service;

    public MfaController(MfaService service) {
        this.service = service;
    }

    public record SetupRequest(@NotBlank String username) {}
    public record SetupResponse(String id, String username, String secret,
                                String otpauthUrl, String qrDataUrl,
                                String qrPngUrl, long expiresAt, long validitySeconds) {}
    public record VerifyRequest(@NotBlank String username, @NotBlank String code) {}

    @PostMapping("/setup")
    @Operation(summary = "Gera segredo TOTP + QR (uso do admin, 300s)")
    public SetupResponse setup(Authentication admin, @Valid @RequestBody SetupRequest req) {
        var s = service.setup(req.username().trim());
        return new SetupResponse(s.id(), s.username(), s.secret(), s.otpauthUrl(),
            s.qrDataUrl(), "/api/v1/mfa/qr/" + s.id() + ".png",
            s.expiresAt(), MfaService.RECORD_TTL_SECONDS);
    }

    @GetMapping(value = "/qr/{id}.png", produces = MediaType.IMAGE_PNG_VALUE)
    @Operation(summary = "PNG do QR para salvar/enviar (válido 300s)")
    public ResponseEntity<byte[]> qr(@PathVariable String id) {
        byte[] png = service.qrPng(id);
        if (png == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).body(png);
    }

    @PostMapping("/verify")
    @Operation(summary = "Valida código TOTP do usuário (público, uso único)")
    public ResponseEntity<?> verify(@Valid @RequestBody VerifyRequest req) {
        String mfaId = service.consume(req.username().trim(), req.code().trim());
        if (mfaId == null) return ResponseEntity.status(401).body(Map.of("ok", false));
        return ResponseEntity.ok(Map.of("ok", true, "mfaId", mfaId));
    }
}
