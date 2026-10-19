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
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import java.io.IOException;
import jakarta.servlet.ServletException;

/**
 * Configuracao do filtro SSO para auto-aprovacao no authorize.
 */
@Configuration
public class SsoAutoConfig {

    @Bean
    public FilterRegistrationBean<SsoCookieCheckFilter> ssoCookieCheckFilter(SsoCookieUtil ssoCookieUtil) {
        FilterRegistrationBean<SsoCookieCheckFilter> bean = new FilterRegistrationBean<>();
        bean.setFilter(new SsoCookieCheckFilter(ssoCookieUtil));
        bean.addUrlPatterns("/oauth2/authorize");
        bean.setOrder(org.springframework.core.Ordered.HIGHEST_PRECEDENCE);
        return bean;
    }

    /**
     * Filtro que verifica o cookie SSO no endpoint /oauth2/authorize.
     *
     * <p>Se o usuario ja tem o cookie SSO (veio do login no ERP), o filtro
     * redireciona de volta para o authorize com o parametro continue, fazendo
     * o Spring Authorization Server aprovar silenciosamente.
     */
    public static class SsoCookieCheckFilter implements jakarta.servlet.Filter {

        private final SsoCookieUtil ssoCookieUtil;

        public SsoCookieCheckFilter(SsoCookieUtil ssoCookieUtil) {
            this.ssoCookieUtil = ssoCookieUtil;
        }

        @Override
        public void doFilter(jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res, jakarta.servlet.FilterChain chain)
                throws java.io.IOException, jakarta.servlet.ServletException {
            jakarta.servlet.http.HttpServletRequest request = (jakarta.servlet.http.HttpServletRequest) req;
            jakarta.servlet.http.HttpServletResponse response = (jakarta.servlet.http.HttpServletResponse) res;

            // So atua no GET do authorize
            if (!"GET".equals(request.getMethod()) || !request.getRequestURI().startsWith("/oauth2/authorize")) {
                chain.doFilter(request, response);
                return;
            }

            // Procura o cookie SSO
            jakarta.servlet.http.Cookie[] cookies = request.getCookies();
            String token = null;
            if (cookies != null) {
                for (jakarta.servlet.http.Cookie c : cookies) {
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

            // Tem cookie: redireciona para o mesmo authorize com continue
            String qs = request.getQueryString();
            String redirect = "/oauth2/authorize" + (qs != null ? "?" + qs : "") + "&continue";
            response.sendRedirect(redirect);
        }
    }
}
