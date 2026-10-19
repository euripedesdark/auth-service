package com.euripedes.authservice.config;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Filtro que verifica o cookie SSO no endpoint /oauth2/authorize.
 *
 * <p>Se o usuario ja tem o cookie SSO (veio do login no ERP), o filtro
 * valida o token e cria uma autenticacao no SecurityContext, permitindo
 * que o authorization server aprove silenciosamente.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SsoAutoApprovalFilter extends OncePerRequestFilter {

    private final JwtDecoder jwtDecoder;

    public SsoAutoApprovalFilter(JwtDecoder jwtDecoder) {
        this.jwtDecoder = jwtDecoder;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws jakarta.servlet.ServletException, IOException {

        String path = request.getRequestURI();

        // So atua no GET do authorize
        if (!"GET".equals(request.getMethod()) || !path.startsWith("/oauth2/authorize")) {
            chain.doFilter(request, response);
            return;
        }

        // Procura o cookie SSO
        Cookie[] cookies = request.getCookies();
        String token = null;
        if (cookies != null) {
            for (Cookie c : cookies) {
                if ("sso_token".equals(c.getName())) {
                    token = c.getValue();
                    break;
                }
            }
        }
        if (token == null || token.isBlank()) {
            chain.doFilter(request, response);
            return;
        }

        // Valida o token do cookie
        try {
            var jwt = jwtDecoder.decode(token);
            if (jwt.isExpired()) {
                chain.doFilter(request, response);
                return;
            }
            // Token valido: cria autenticacao e aprova
            Authentication auth = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                    jwt.getSubject(), null, List.of());
            SecurityContextHolder.getContext().setAuthentication(auth);
        } catch (Exception e) {
            // Token invalido: segue fluxo normal
        }
        chain.doFilter(request, response);
    }
}
