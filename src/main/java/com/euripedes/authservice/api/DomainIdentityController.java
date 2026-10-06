package com.euripedes.authservice.api;

import com.euripedes.authservice.contract.IdentityDto;
import com.euripedes.authservice.provider.DomainAuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/identity")
@Tag(name = "Identity")
public class DomainIdentityController {
    private final DomainAuthService service;

    public DomainIdentityController(DomainAuthService service) {
        this.service = service;
    }

    @PostMapping("/authenticate-domain")
    @Operation(summary = "Autentica em outro AD pelo nome do domínio (DNS da máquina)")
    public IdentityDto authenticateDomain(@Valid @RequestBody DomainLoginRequest req) {
        return service.authenticate(req.username(), req.password(), req.domain());
    }

    @GetMapping("/resolve-domain")
    @Operation(summary = "Mostra onde o domínio resolve (diagnóstico DNS/LDAPS)")
    public ResponseEntity<?> resolve(@RequestParam String domain) {
        try {
            var r = service.resolve(domain);
            return ResponseEntity.ok(Map.of("domain", r.domain(), "ips", r.ips(),
                "ldapUrl", r.ldapUrl(), "baseDn", r.baseDn()));
        } catch (Exception e) {
            return ResponseEntity.status(404).body(Map.of("error", "Dominio nao resolvido no DNS"));
        }
    }
}
