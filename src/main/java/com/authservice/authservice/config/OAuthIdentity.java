package com.authservice.authservice.config;

import java.security.Principal;
import java.util.List;

/**
 * Identidade do AD no contexto do OAuth2.
 *
 * <p>E' o principal da autenticacao feita na tela de login do
 * authorization server. Guarda os grupos porque as claims do id_token sao
 * montadas a partir daqui: e' dai que o Grafana descobre de que grupos o
 * usuario faz parte.
 */
public record OAuthIdentity(String identityId,
                            String username,
                            String provider,
                            List<String> groups) implements Principal {

    @Override
    public String getName() {
        return username;
    }
}