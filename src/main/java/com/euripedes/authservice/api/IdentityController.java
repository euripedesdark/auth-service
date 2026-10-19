package com.euripedes.authservice.api;

import com.euripedes.authservice.config.SsoCookieUtil;
import com.euripedes.authservice.contract.IdentityDto;
import com.euripedes.authservice.contract.LoginRequestDto;
import com.euripedes.authservice.contract.TokenResponseDto;
import com.euripedes.authservice.service.AuthenticationService;
import com.euripedes.authservice.service.TokenService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
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
    private final SsoCookieUtil ssoCookieUtil;

    public IdentityController(AuthenticationService service, TokenService tokenService, SsoCookieUtil ssoCookieUtil) {
        this.service = service;
        this.tokenService = tokenService;
        this.ssoCookieUtil = ssoCookieUtil;
    }

    @GetMapping("/me") @Operation(summary="Return the authenticated identity")
    public IdentityDto me(Authentication a){return identityFrom(a);}
    @GetMapping("/groups") @Operation(summary="Return the authenticated identity and groups")
    public IdentityDto groups(Authentication a){return identityFrom(a);}
    @GetMapping("/provider") @Operation(summary="Return the authenticated identity and provider")
    public IdentityDto provider(Authentication a){return identityFrom(a);}
    @GetMapping("/authenticated") @Operation(summary="Return the authenticated identity")
    public IdentityDto authenticated(Authentication a){return identityFrom(a);}

    @PostMapping("/authenticate") @Operation(summary="Authenticate an identity against Active Directory")
    public IdentityDto authenticate(@Valid @RequestBody LoginRequestDto request, jakarta.servlet.http.HttpServletResponse response){
        IdentityDto identity = service.authenticate(request);
        String token = tokenService.issue(identity).accessToken();
        ssoCookieUtil.setToken(response, token);
        return identity;
    }

    @PostMapping("/token") @Operation(summary="Authenticate against Active Directory and issue a short-lived Bearer token")
    public TokenResponseDto token(@Valid @RequestBody LoginRequestDto request){
        return tokenService.issue(service.authenticate(request));
    }

    private IdentityDto identityFrom(Authentication authentication){
        if(authentication==null||!authentication.isAuthenticated())
            throw new org.springframework.security.authentication.AuthenticationCredentialsNotFoundException("Not authenticated");
        if(authentication.getPrincipal() instanceof Jwt jwt){
            return new IdentityDto(jwt.getSubject(),jwt.getClaimAsString("username"),
                jwt.getClaimAsString("provider"),jwt.getClaimAsStringList("groups"));
        }
        return service.resolve(authentication);
    }
}
