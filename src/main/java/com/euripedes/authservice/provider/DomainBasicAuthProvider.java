package com.euripedes.authservice.provider;

import java.util.List;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Basic Auth contra outro AD: quando o request traz X-Domain,
 * valida na hora contra ldaps://dominio (sem config fixa).
 */
@Component
public class DomainBasicAuthProvider implements AuthenticationProvider {
    private final DomainAuthService domainAuthService;

    public DomainBasicAuthProvider(DomainAuthService domainAuthService) {
        this.domainAuthService = domainAuthService;
    }

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        var attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        String domain = attrs != null
            ? attrs.getRequest().getHeader("X-Domain") : null;
        if (domain == null || domain.isBlank()) return null;
        try {
            var id = domainAuthService.authenticate(authentication.getName(),
                String.valueOf(authentication.getCredentials()), domain.trim());
            return UsernamePasswordAuthenticationToken.authenticated(
                id.username(), null, List.of());
        } catch (BadCredentialsException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new BadCredentialsException("Invalid credentials", e);
        }
    }

    @Override
    public boolean supports(Class<?> type) {
        return UsernamePasswordAuthenticationToken.class.isAssignableFrom(type);
    }
}
