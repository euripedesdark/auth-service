package com.authservice.authservice.config;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuracao do servidor OAuth2/OIDC deste auth-service.
 *
 * <p>Existe para o Grafana deixar de ter login proprio: o painel passa a
 * autenticar contra este servico, e a identidade e os grupos do AD chegam
 * no id_token e no userinfo. O resto dos consumidores continua nos
 * contratos de sempre ({@code /api/v1/identity/*}).
 *
 * <p>Fica desligado por padrao. Sem {@code AUTH_OAUTH2_ENABLED=true} nenhuma
 * rota OAuth e registrada e o servico se comporta como sempre.
 */
@ConfigurationProperties(prefix = "auth.oauth2")
public class OAuth2ServerProperties {

    /** Liga as rotas OAuth2/OIDC. Desligado por padrao. */
    private boolean enabled = false;

    /**
     * Issuer do token. Precisa ser uma URL absoluta e igual a que o Grafana
     * vai usar, porque e' com ela que ele descobre os endpoints e valida o
     * {@code iss} do id_token.
     */
    private String issuer = "http://192.168.2.10:8181";

    /** Client id do Grafana. */
    private String clientId = "grafana";

    /** Client secret do Grafana. Vazio impede o registro do cliente. */
    private String clientSecret = "";

    /** Para onde o Grafana devolve o usuario depois do login. */
    private List<String> redirectUris = new ArrayList<>(List.of(
            "http://192.168.2.10:3000/grafana/login/generic_oauth"));

    /** Onde a chave RSA do issuer fica. Criada no primeiro start se faltar. */
    private String jwkDirectory = "/opt/auth-service/oauth-jwks";

    /** Validade do access token. */
    private long accessTokenSeconds = 900;

    /** Dominio montado para a claim email, quando o Grafana exige uma. */
    private String emailDomain = "srvcloud.cloud";

    /**
     * Origem do ERP, a unica autorizada a embutir esta tela num iframe.
     * Sem isso o Spring Security bloquearia o formulario de login dentro
     * do painel, porque ele vem em outra porta.
     */
    private String erpOrigin = "http://192.168.2.10:8080";

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getIssuer() { return issuer; }
    public void setIssuer(String issuer) { this.issuer = issuer; }
    public String getClientId() { return clientId; }
    public void setClientId(String clientId) { this.clientId = clientId; }
    public String getClientSecret() { return clientSecret; }
    public void setClientSecret(String clientSecret) { this.clientSecret = clientSecret; }
    public List<String> getRedirectUris() { return redirectUris; }
    public void setRedirectUris(List<String> redirectUris) { this.redirectUris = redirectUris; }
    public String getJwkDirectory() { return jwkDirectory; }
    public void setJwkDirectory(String jwkDirectory) { this.jwkDirectory = jwkDirectory; }
    public long getAccessTokenSeconds() { return accessTokenSeconds; }
    public void setAccessTokenSeconds(long accessTokenSeconds) { this.accessTokenSeconds = accessTokenSeconds; }
    public String getEmailDomain() { return emailDomain; }
    public void setEmailDomain(String emailDomain) { this.emailDomain = emailDomain; }
    public String getErpOrigin() { return erpOrigin; }
    public void setErpOrigin(String erpOrigin) { this.erpOrigin = erpOrigin; }
}