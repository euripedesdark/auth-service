package com.euripedes.authservice.api;

import com.euripedes.authservice.contract.IdentityDto;
import com.euripedes.authservice.contract.LoginRequestDto;
import com.euripedes.authservice.service.AuthenticationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/identity")
@Tag(name="Identity")
public class IdentityController {
    private final AuthenticationService service;
    public IdentityController(AuthenticationService service){this.service=service;}
    @GetMapping("/me") @Operation(summary="Return the authenticated identity")
    public IdentityDto me(Authentication a){return service.resolve(a);}
    @GetMapping("/groups") @Operation(summary="Return the authenticated identity and groups")
    public IdentityDto groups(Authentication a){return service.resolve(a);}
    @GetMapping("/provider") @Operation(summary="Return the authenticated identity and provider")
    public IdentityDto provider(Authentication a){return service.resolve(a);}
    @GetMapping("/authenticated") @Operation(summary="Return the authenticated identity")
    public IdentityDto authenticated(Authentication a){return service.resolve(a);}
    @PostMapping("/authenticate") @Operation(summary="Authenticate an identity")
    public IdentityDto authenticate(@Valid @RequestBody LoginRequestDto request){return service.authenticate(request);}
}
