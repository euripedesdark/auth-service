package com.euripedes.authservice.config;

import com.euripedes.authservice.provider.AdProvider;
import com.euripedes.authservice.provider.ProviderUnavailableException;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http,AdProvider adProvider)throws Exception{
        http.csrf(c->c.disable())
            .authorizeHttpRequests(a->a
                .requestMatchers("/api/v1/identity/authenticate","/v3/api-docs/**","/swagger-ui/**","/swagger-ui.html").permitAll()
                .requestMatchers(HttpMethod.GET,"/api/v1/identity/**").authenticated()
                .anyRequest().denyAll())
            .authenticationProvider(adProvider)
            .httpBasic(Customizer.withDefaults())
            .exceptionHandling(e->e
                .authenticationEntryPoint((req,res,ex)->{
                    if(ex.getCause() instanceof ProviderUnavailableException || (ex.getMessage()!=null&&ex.getMessage().contains("unavailable")))
                        res.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
                    else res.sendError(HttpServletResponse.SC_UNAUTHORIZED);
                })
                .accessDeniedHandler((req,res,ex)->res.sendError(HttpServletResponse.SC_FORBIDDEN)));
        return http.build();
    }
}
