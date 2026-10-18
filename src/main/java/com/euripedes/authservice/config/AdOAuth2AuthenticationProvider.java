package com.euripedes.authservice.config;

import com.euripedes.authservice.contract.LoginRequestDto;
import com.euripedes.authservice.provider.AdProvider;
import java.util.ArrayList;
import java.util.List;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/**
 * Autentica a tela de login do authorization server contra o AD.
 *
 * <p>Delega ao {@link AdProvider}, que ja faz a validacao de senha, e
 * guarda a identidade inteira como principal — inclusive os grupos. O
 * {@code AdProvider} original devolve so o nome do usuario, e sem os grupos
 * as claims do id_token sairam vazias.
 *
 * <p>Sao separados de proposito: o fluxo antigo de {@code /api/v1/identity}
 * continua com o provider original e nao sente nenhuma mudanca.
 */
public class AdOAuth2AuthenticationProvider implements AuthenticationProvider {

    private final AdProvider adProvider;

    public AdOAuth2AuthenticationProvider(AdProvider adProvider) {
        this.adProvider = adProvider;
    }

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        LoginRequestDto request = new LoginRequestDto(
                authentication.getName(),
                String.valueOf(authentication.getCredentials()),
                AdProvider.NAME);
        var identity = adProvider.authenticate(request);

        List<GrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority("ROLE_USER"));
        if (identity.groups() != null) {
            identity.groups().stream()
                    .filter(g -> g != null && !g.isBlank())
                    .map(g -> new SimpleGrantedAuthority("GROUP_" + g))
                    .forEach(authorities::add);
        }

        return UsernamePasswordAuthenticationToken.authenticated(
                new OAuthIdentity(identity.identityId(), identity.username(),
                        identity.provider(), identity.groups()),
                null,
                authorities);
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication);
    }
}