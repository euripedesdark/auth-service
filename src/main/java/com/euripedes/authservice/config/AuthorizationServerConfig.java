package com.euripedes.authservice.config;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jwt.JWTParser;
import java.text.ParseException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer.FrameOptionsConfig;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.server.authorization.client.InMemoryRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configurers.OAuth2AuthorizationServerConfigurer;
import org.springframework.security.oauth2.server.authorization.oidc.authentication.OidcUserInfoAuthenticationContext;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

/**
 * Authorization server OAuth2/OIDC deste auth-service.
 *
 * <p>Existe para o Grafana deixar de ter login proprio: o painel passa a
 * autenticar contra este servico e recebe usuario e grupos do AD. O que o
 * Grafana consome:
 *
 * <ul>
 *   <li>{@code /oauth2/authorize} — login por formulario contra o AD</li>
 *   <li>{@code /oauth2/token} — authorization_code + PKCE</li>
 *   <li>{@code /oauth2/userinfo} — usuario e grupos</li>
 *   <li>{@code /oauth2/jwks} — chave publica para validar o id_token</li>
 * </ul>
 *
 * <p>A cadeia roda <b>antes</b> da de {@code /api/**}, que fica como
 * estava: so o fluxo OAuth e' novo. Precisa de {@code IF_REQUIRED}, porque
 * o authorization server guarda o pedido de autorizacao na sessao, e de
 * CSRF ligado, porque o login e' POST.
 *
 * <p>Esta tudo desligado por padrao. Sem {@code auth.oauth2.enabled=true}
 * nenhuma rota e registrada e o servico se comporta como sempre.
 */
@Configuration
@ConditionalOnProperty(prefix = "auth.oauth2", name = "enabled", havingValue = "true")
public class AuthorizationServerConfig {

    private static final Logger LOGGER = LoggerFactory.getLogger(AuthorizationServerConfig.class);

    private static final String ID_TOKEN = "id_token";
    private static final String LOGIN_PATH = "/oauth2/login";

    @Bean
    @Order(1)
    public SecurityFilterChain authorizationServerSecurityFilterChain(
            HttpSecurity http,
            OAuth2ServerProperties properties,
            AdOAuth2AuthenticationProvider adOAuth2AuthenticationProvider) throws Exception {

        OAuth2AuthorizationServerConfigurer authorizationServer = new OAuth2AuthorizationServerConfigurer();

        // A tela de login nao e' endpoint do authorization server, mas
        // precisa cair nesta cadeia: na outra ha denyAll().
        RequestMatcher loginPage = new OrRequestMatcher(
                new AntPathRequestMatcher(LOGIN_PATH, "GET"),
                new AntPathRequestMatcher(LOGIN_PATH, "POST"));

        http.securityMatcher(new OrRequestMatcher(loginPage, oauthEndpoints()))
            .with(authorizationServer, as -> as.oidc(oidc -> oidc
                    .userInfoEndpoint(userInfo -> userInfo.userInfoMapper(AuthorizationServerConfig::userInfo))))
            .authorizeHttpRequests(a -> a
                    .requestMatchers(LOGIN_PATH).permitAll()
                    .anyRequest().authenticated())
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
            // CSRF so no que o navegador envia: a tela de login. Os
            // endpoints de maquina (/oauth2/token e companhia) sao chamados
            // pelo Grafana, nao por um form, e exigirem token de CSRF ali
            // devolvia 401 em toda troca de code por token.
            .csrf(csrf -> csrf.ignoringRequestMatchers(machineEndpoints()))
            .formLogin(form -> form.loginPage(LOGIN_PATH).permitAll())
            // O painel roda dentro de um iframe do ERP, em outra porta.
            // SameOrigin barraria e denyDefaultHeaders tiraria o formulario
            // de dentro do iframe; frame-ancestors limita ao ERP.
            .headers(headers -> headers
                    .frameOptions(FrameOptionsConfig::disable)
                    .contentSecurityPolicy(csp -> csp.policyDirectives(
                            "frame-ancestors 'self' " + properties.getErpOrigin())))
            .exceptionHandling(e -> e.defaultAuthenticationEntryPointFor(
                    new LoginUrlAuthenticationEntryPoint(LOGIN_PATH),
                    new org.springframework.security.web.util.matcher.MediaTypeRequestMatcher(
                            MediaType.TEXT_HTML)))
            // O /userinfo chega com um Bearer. A cadeia precisa de um
            // resource server que valide esse token com a chave RSA do issuer;
            // sem isso o filtro de userinfo ve um pedido anonimo e devolve
            // 302 para o login.
            .oauth2ResourceServer(resourceServer -> resourceServer.jwt(jwt -> jwt
                    .decoder(issuerJwtDecoder(properties))))
            .authenticationProvider(adOAuth2AuthenticationProvider);

        return http.build();
    }

    /**
     * Valida os tokens que o proprio issuer emitiu.
     *
     * <p>Fica montado aqui, e nao como bean, porque um {@code JwtDecoder} no
     * contexto seria usado tambem pela cadeia de {@code /api/**}, que valida
     * o token interno em HS256 e recusaria os tokens RSA do issuer.
     */
    private static JwtDecoder issuerJwtDecoder(OAuth2ServerProperties properties) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder
                .withJwkSetUri(properties.getIssuer() + "/oauth2/jwks")
                .build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(properties.getIssuer()));
        return decoder;
    }

    /** Rotas do authorization server e da OIDC. */
    private static RequestMatcher oauthEndpoints() {
        return new OrRequestMatcher(
                new AntPathRequestMatcher("/oauth2/**"),
                new AntPathRequestMatcher("/userinfo"),
                new AntPathRequestMatcher("/.well-known/oauth-authorization-server"),
                new AntPathRequestMatcher("/.well-known/oauth-authorization-server/**"),
                new AntPathRequestMatcher("/.well-known/openid-configuration"),
                new AntPathRequestMatcher("/.well-known/oauth-protected-resource/**"));
    }

    /**
     * Endpoints chamados por outro sistema, sem navegador no meio: troca de
     * code por token, introspeccao, revogacao e registro de cliente.
     */
    private static RequestMatcher machineEndpoints() {
        return new OrRequestMatcher(
                new AntPathRequestMatcher("/oauth2/token"),
                new AntPathRequestMatcher("/oauth2/introspect"),
                new AntPathRequestMatcher("/oauth2/revoke"),
                new AntPathRequestMatcher("/oauth2/device_authorization"),
                new AntPathRequestMatcher("/oauth2/register", "POST"));
    }

    @Bean
    public AdOAuth2AuthenticationProvider adOAuth2AuthenticationProvider(
            com.euripedes.authservice.provider.AdProvider adProvider) {
        return new AdOAuth2AuthenticationProvider(adProvider);
    }

    @Bean
    public RegisteredClientRepository registeredClientRepository(OAuth2ServerProperties properties) {
        String secret = properties.getClientSecret() == null ? "" : properties.getClientSecret().trim();
        LOGGER.info("OAuth2: cliente [{}] redirect={} issuer={} secret {}",
                properties.getClientId(), properties.getRedirectUris(), properties.getIssuer(),
                secret.isEmpty() ? "VAZIO" : "definido (" + secret.length() + " caracteres)");
        String secretArmazenado = secret.isEmpty()
                ? ""
                : PasswordEncoderFactories.createDelegatingPasswordEncoder().encode(secret);

        RegisteredClient grafana = RegisteredClient.withId(UUID.randomUUID().toString())
                .clientId(properties.getClientId())
                .clientSecret(secretArmazenado)
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUris(uris -> uris.addAll(properties.getRedirectUris()))
                .scopes(scopes -> {
                    scopes.add("openid");
                    scopes.add("profile");
                    scopes.add("email");
                    scopes.add("groups");
                })
                .clientSettings(ClientSettings.builder()
                        .requireAuthorizationConsent(false)
                        .requireProofKey(false)
                        .build())
                .tokenSettings(TokenSettings.builder()
                        .accessTokenTimeToLive(Duration.ofSeconds(properties.getAccessTokenSeconds()))
                        .build())
                .build();

        return new InMemoryRegisteredClientRepository(grafana);
    }

    @Bean
    public AuthorizationServerSettings authorizationServerSettings(OAuth2ServerProperties properties) {
        return AuthorizationServerSettings.builder()
                .issuer(properties.getIssuer())
                .build();
    }

    /**
     * Chave RSA do issuer.
     *
     * <p>Fica em arquivo, e nao em memoria, porque o Grafana guarda a
     * sessao criada com um token assinado por ela: se a chave mudasse a
     * cada reinicio, todo mundo seria deslogado.
     */
    @Bean
    public JWKSource<SecurityContext> jwkSource(OAuth2ServerProperties properties) throws JOSEException {
        JWKSet set = new JWKSet(loadOrCreateKey(properties.getJwkDirectory()));
        return (selector, context) -> selector.select(set);
    }

    /**
     * Claims do id_token: quem e' a pessoa e de que grupos ela faz parte.
     * E' daqui que o Grafana tira a permissao, alem do login em si.
     */
    @Bean
    public OAuth2TokenCustomizer<JwtEncodingContext> jwtTokenCustomizer(OAuth2ServerProperties properties) {
        return context -> {
            if (!ID_TOKEN.equals(context.getTokenType().getValue())) {
                return;
            }
            Authentication principal = context.getPrincipal();
            Object subject = principal == null ? null : principal.getPrincipal();
            if (subject instanceof OAuthIdentity identity) {
                context.getClaims().claim("preferred_username", identity.username());
                context.getClaims().claim("name", identity.username());
                context.getClaims().claim("email",
                        identity.username() + "@" + properties.getEmailDomain());
                context.getClaims().claim("groups",
                        identity.groups() == null ? List.of() : identity.groups());
                context.getClaims().claim("provider", identity.provider());
                context.getClaims().claim("grafana_role", grafanaRole(identity.groups()));
            }
        };
    }

    /**
     * Papel do Grafana derivado dos grupos do AD.
     *
     * <p>Espelha a regra do ERP: quem e' superusuario, Domain Admin ou esta
     * na Diretoria enxerga tudo e pode mexer no painel; o resto entra como
     * Viewer e so ve o que o ERP mandou filtrar. O Grafana so aceita os
     * nomes exatos das suas funcoes, entao o valor tem de sair assim.
     */
    static String grafanaRole(List<String> groups) {
        if (groups == null) {
            return "Viewer";
        }
        boolean podeEditar = groups.stream().anyMatch(g -> g != null && (
                g.equalsIgnoreCase("GRP_SUPERUSER")
                        || g.equalsIgnoreCase("GRP_DIRETORIA")
                        || g.equalsIgnoreCase("Domain Admins")));
        return podeEditar ? "Editor" : "Viewer";
    }


    private RSAKey loadOrCreateKey(String directory) throws JOSEException {
        Path dir = Path.of(directory);
        Path privateFile = dir.resolve("oauth-jwks-private.pem");
        Path publicFile = dir.resolve("oauth-jwks-public.pem");
        try {
            RSAPrivateKey privateKey;
            RSAPublicKey publicKey;
            if (Files.exists(privateFile) && Files.exists(publicFile)) {
                privateKey = (RSAPrivateKey) readPrivateKey(Files.readString(privateFile));
                publicKey = (RSAPublicKey) readPublicKey(Files.readString(publicFile));
            } else {
                KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
                generator.initialize(2048, SecureRandom.getInstanceStrong());
                KeyPair pair = generator.generateKeyPair();
                privateKey = (RSAPrivateKey) pair.getPrivate();
                publicKey = (RSAPublicKey) pair.getPublic();
                Files.createDirectories(dir);
                Files.writeString(privateFile, toPem("PRIVATE KEY", privateKey.getEncoded()));
                Files.writeString(publicFile, toPem("PUBLIC KEY", publicKey.getEncoded()));
            }
            return signingKey(publicKey, privateKey);
        } catch (IOException e) {
            throw new UncheckedIOException("Falha ao preparar a chave do issuer em " + directory, e);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Nao foi possivel ler ou gerar a chave RSA do issuer", e);
        }
    }

    /**
     * Monta a chave de assinatura.
     *
     * <p>O keyID nao e' opcional: o NimbusJwtEncoder do Spring escolhe a
     * chave pelo identificador, e sem ele a troca de code por token falha com
     * "Failed to select a JWK signing key". O thumbprint mantem o mesmo id
     * entre reinicios, desde que a chave em disco nao mude.
     */
    private static RSAKey signingKey(RSAPublicKey publicKey, RSAPrivateKey privateKey) throws JOSEException {
        return new RSAKey.Builder(publicKey)
                .privateKey(privateKey)
                .keyUse(KeyUse.SIGNATURE)
                .algorithm(JWSAlgorithm.RS256)
                .keyIDFromThumbprint()
                .build();
    }

    private static String toPem(String label, byte[] der) {
        String base64 = Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(der);
        return "-----BEGIN " + label + "-----\n" + base64 + "\n-----END " + label + "-----\n";
    }

    private static PrivateKey readPrivateKey(String pem) throws GeneralSecurityException {
        byte[] der = Base64.getMimeDecoder().decode(strip(pem));
        return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
    }

    private static PublicKey readPublicKey(String pem) throws GeneralSecurityException {
        byte[] der = Base64.getMimeDecoder().decode(strip(pem));
        return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(der));
    }

    private static String strip(String pem) {
        String semMarcacao = pem.replaceAll("-----BEGIN [A-Z ]+-----", "")
                .replaceAll("-----END [A-Z ]+-----", "");
        return semMarcacao.replaceAll("\\s", "");
    }

    /**
     * Claims do /userinfo.
     *
     * <p>O mapper padrao do Spring so repassa as claims padrao do OIDC e
     * descarta o resto. O Grafana precisa de {@code grafana_role} e dos
     * grupos, entao elas voltam daqui.
     *
     * <p>No userinfo o principal e' o {@link Jwt} do access token, e nao um
     * {@code OidcUser} — por isso a leitura e' feita pelo Jwt.
     */
    private static OidcUserInfo userInfo(OidcUserInfoAuthenticationContext context) {
        Map<String, Object> idToken = idTokenClaims(context);
        if (idToken != null) {
            String subject = asText(idToken.get("sub"));
            String preferredUsername = asText(idToken.get("preferred_username"));
            String email = asText(idToken.get("email"));
            String role = asText(idToken.get("grafana_role"));

            Map<String, Object> claims = new LinkedHashMap<>();
            claims.put("sub", subject);
            claims.put("preferred_username", preferredUsername == null ? subject : preferredUsername);
            putIfPresent(claims, "name", asText(idToken.get("name")));
            claims.put("email", email == null ? subject : email);
            putIfPresent(claims, "provider", asText(idToken.get("provider")));
            List<String> groups = asTextList(idToken.get("groups"));
            claims.put("groups", groups);
            claims.put("grafana_role", role == null ? "Viewer" : role);
            return new OidcUserInfo(claims);
        }
        LOGGER.warn("userinfo sem id_token na autorizacao; devolvendo identidade vazia");
        return new OidcUserInfo(Map.of("sub", "desconhecido"));
    }

    /**
     * Claims do id_token guardado na autorizacao.
     *
     * <p>Sai de la em vez do principal do pedido porque o tipo do principal
     * no userinfo mudou entre versoes do Spring. O token foi emitido aqui
     * mesmo, entao ler as claims direto nao precisa de validacao.
     */
    private static Map<String, Object> idTokenClaims(OidcUserInfoAuthenticationContext context) {
        OAuth2Authorization authorization = context.getAuthorization();
        if (authorization == null) {
            return null;
        }
        OAuth2Authorization.Token<OidcIdToken> idToken = authorization.getToken(OidcIdToken.class);
        if (idToken == null || idToken.getToken() == null) {
            return null;
        }
        try {
            return new LinkedHashMap<>(JWTParser.parse(idToken.getToken().getTokenValue()).getJWTClaimsSet().getClaims());
        } catch (ParseException e) {
            LOGGER.warn("id_token da autorizacao nao pode ser lido: {}", e.getMessage());
            return null;
        }
    }

    private static void putIfPresent(Map<String, Object> claims, String key, String value) {
        if (value != null && !value.isBlank()) {
            claims.put(key, value);
        }
    }

    private static String asText(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static List<String> asTextList(Object value) {
        if (!(value instanceof List<?> lista)) {
            return List.of();
        }
        return lista.stream().map(String::valueOf).toList();
    }
}
