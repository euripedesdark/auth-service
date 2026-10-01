package com.euripedes.authservice.api;

import com.euripedes.authservice.contract.IdentityDto;
import com.euripedes.authservice.contract.LoginRequestDto;
import com.euripedes.authservice.contract.TokenResponseDto;
import com.euripedes.authservice.service.AuthenticationService;
import com.euripedes.authservice.service.TokenService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/identity")
@Tag(name="Identity")
public class IdentityController {
    private final AuthenticationService service;
    private final TokenService tokenService;
    public IdentityController(AuthenticationService service,TokenService tokenService){this.service=service;this.tokenService=tokenService;}
    @GetMapping("/me") public IdentityDto me(Authentication a){return identityFrom(a);}
    @GetMapping("/groups") public IdentityDto groups(Authentication a){return identityFrom(a);}
    @GetMapping("/provider") public IdentityDto provider(Authentication a){return identityFrom(a);}
    @GetMapping("/authenticated") public IdentityDto authenticated(Authentication a){return identityFrom(a);}
    @PostMapping("/authenticate") @Operation(summary="Authenticate against Active Directory and issue an access token")
    public TokenResponseDto authenticate(@Valid @RequestBody LoginRequestDto request){return tokenService.issue(service.authenticate(request));}
    private IdentityDto identityFrom(Authentication a){
        if(a==null||!a.isAuthenticated()) throw new org.springframework.security.authentication.AuthenticationCredentialsNotFoundException("Not authenticated");
        if(a.getPrincipal() instanceof Jwt jwt) return new IdentityDto(jwt.getSubject(),jwt.getClaimAsString("username"),jwt.getClaimAsString("provider"),jwt.getClaimAsStringList("groups"));
        return service.resolve(a);
    }
}