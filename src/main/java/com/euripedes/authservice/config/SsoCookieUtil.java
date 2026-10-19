package com.euripedes.authservice.config;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;

/**
 * Utilitario para o cookie de sessao SSO.
 *
 * <p>O cookie e' httpOnly, SameSite=Lax e vale para todo o host
 * (192.168.2.10). Isso permite que o ERP (porta 8080) e o Grafana
 * (porta 3000) compartilhem a mesma sessao, ja que estao no mesmo host.
 */
@Component
public final class SsoCookieUtil {

    private static final String COOKIE_NAME = "sso_token";
    private static final String COOKIE_DOMAIN = "192.168.2.10";
    private static final String COOKIE_PATH = "/";
    private static final int MAX_AGE_SECONDS = 8 * 3600; // 8 horas

    private SsoCookieUtil() {}

    /**
     * Grava o token no cookie SSO.
     */
    public void setToken(HttpServletResponse response, String token) {
        Cookie cookie = new Cookie(COOKIE_NAME, token);
        cookie.setHttpOnly(true);
        cookie.setSecure(false); // HTTP em dev; em prod HTTPS
        cookie.setPath(COOKIE_PATH);
        cookie.setDomain(COOKIE_DOMAIN);
        cookie.setMaxAge(MAX_AGE_SECONDS);
        cookie.setAttribute("SameSite", "Lax");
        response.addCookie(cookie);
    }

    /**
     * Apaga o cookie no logout.
     */
    public void clear(HttpServletResponse response) {
        Cookie cookie = new Cookie(COOKIE_NAME, "");
        cookie.setHttpOnly(true);
        cookie.setPath(COOKIE_PATH);
        cookie.setDomain(COOKIE_DOMAIN);
        cookie.setMaxAge(0);
        cookie.setAttribute("SameSite", "Lax");
        response.addCookie(cookie);
    }
}
