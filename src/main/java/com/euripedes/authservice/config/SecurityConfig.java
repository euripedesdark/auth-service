package com.euripedes.authservice.config;

import com.euripedes.authservice.provider.AdProvider;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http,AdProvider adProvider)throws Exception{
        http.csrf(c->c.disable())
            .sessionManagement(s->s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .requestCache(c->c.disable()).formLogin(f->f.disable()).logout(l->l.disable())
            .authorizeHttpRequests(a->a
                .requestMatchers("/api/v1/identity/authenticate","/api/v1/identity/token",
                    "/v3/api-docs/**","/swagger-ui/**","/swagger-ui.html").permitAll()
                .requestMatchers("/actuator/health").permitAll()
                .requestMatchers("/","/admin","/admin/","/admin/**","/mfa","/mfa/","/mfa/**").permitAll()
                .requestMatchers(HttpMethod.POST,"/api/v1/mfa/verify").permitAll()
                .requestMatchers(HttpMethod.POST,"/api/v1/certificates/download-token").permitAll()
                .requestMatchers(HttpMethod.GET,"/api/v1/mfa/qr/**","/api/v1/certificates/download").permitAll()
                .requestMatchers(HttpMethod.POST,"/api/v1/mfa/setup","/api/v1/certificates/issue").authenticated()
                .requestMatchers(HttpMethod.GET,"/api/v1/identity/**","/api/v1/certificates/pending").authenticated()
                .anyRequest().denyAll())
            .authenticationProvider(adProvider)
            .httpBasic(Customizer.withDefaults())
            .oauth2ResourceServer(o->o.jwt(Customizer.withDefaults()))
            .headers(h->h.contentTypeOptions(Customizer.withDefaults())
                .frameOptions(f->f.deny())
                .httpStrictTransportSecurity(Customizer.withDefaults()))
            .exceptionHandling(e->e
                .authenticationEntryPoint((req,res,ex)->res.sendError(HttpServletResponse.SC_UNAUTHORIZED))
                .accessDeniedHandler((req,res,ex)->res.sendError(HttpServletResponse.SC_FORBIDDEN)));
        return http.build();
    }
}
