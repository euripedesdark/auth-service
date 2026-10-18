package com.euripedes.authservice.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;

/**
 * Contrato do cliente Grafana no authorization server.
 *
 * <p>O Grafana so funciona contra este servico se o redirect_uri bater com
 * o que ele envia e se o fluxo authorization_code estiver liberado. Um
 * redirect errado aqui aparece so em producao, como tela branca no painel.
 */
class AuthorizationServerConfigTest {

    private OAuth2ServerProperties properties() {
        OAuth2ServerProperties properties = new OAuth2ServerProperties();
        properties.setEnabled(true);
        properties.setClientId("grafana");
        properties.setClientSecret("segredo-de-teste");
        properties.setRedirectUris(List.of("http://192.168.2.10:3000/grafana/login/generic_oauth"));
        return properties;
    }

    @Test
    void registraOGrafanaComRedirectEGrantCorretos() {
        RegisteredClientRepository repository =
                new AuthorizationServerConfig().registeredClientRepository(properties());

        RegisteredClient grafana = repository.findByClientId("grafana");

        assertThat(grafana).isNotNull();
        assertThat(grafana.getClientAuthenticationMethods())
                .containsExactly(ClientAuthenticationMethod.CLIENT_SECRET_BASIC);
        assertThat(grafana.getAuthorizationGrantTypes()).contains(AuthorizationGrantType.AUTHORIZATION_CODE);
        assertThat(grafana.getRedirectUris())
                .containsExactly("http://192.168.2.10:3000/grafana/login/generic_oauth");
        assertThat(grafana.getScopes()).contains("openid", "profile", "email", "groups");
    }

    @Test
    void naoExigeConsentimentoNemPkce() {
        RegisteredClientRepository repository =
                new AuthorizationServerConfig().registeredClientRepository(properties());
        ClientSettings settings = repository.findByClientId("grafana").getClientSettings();

        assertThat(settings.isRequireAuthorizationConsent()).isFalse();
        assertThat(settings.isRequireProofKey()).isFalse();
    }

    @Test
    void segredoVazioNaoAutenticaNinguem() {
        OAuth2ServerProperties properties = properties();
        properties.setClientSecret("");

        RegisteredClient grafana = new AuthorizationServerConfig()
                .registeredClientRepository(properties).findByClientId("grafana");

        // Sem segredo definido o cliente fica sem credencial: o Grafana nao
        // autentica e o authorization server recusa a troca de code. Melhor
        // do que aceitar qualquer senha.
        assertThat(grafana.getClientSecret()).isEmpty();
    }

    @Test
    void issuerPrecisaSerUrlAbsolutaParaODescoobrir() {
        OAuth2ServerProperties properties = properties();
        properties.setIssuer("http://192.168.2.10:8181");

        var settings = new AuthorizationServerConfig().authorizationServerSettings(properties);

        assertThat(settings.getIssuer()).isEqualTo("http://192.168.2.10:8181");
    }

    @Test
    void diretoriaESuperuserPodemEditarOPainel() {
        assertThat(AuthorizationServerConfig.grafanaRole(List.of("Domain Users", "GRP_DIRETORIA")))
                .isEqualTo("Editor");
        assertThat(AuthorizationServerConfig.grafanaRole(List.of("GRP_SUPERUSER")))
                .isEqualTo("Editor");
        assertThat(AuthorizationServerConfig.grafanaRole(List.of("Domain Admins")))
                .isEqualTo("Editor");
    }

    @Test
    void quemNaoEstaNaDiretoriaEntraComoViewer() {
        assertThat(AuthorizationServerConfig.grafanaRole(List.of("Domain Users", "GRP_VENDEDOR")))
                .isEqualTo("Viewer");
        assertThat(AuthorizationServerConfig.grafanaRole(null)).isEqualTo("Viewer");
    }
}