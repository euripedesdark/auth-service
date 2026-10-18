package com.euripedes.authservice.config;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWKSet;
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
            .csrf(Customizer.withDefaults())
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
            .authenticationProvider(adOAuth2AuthenticationProvider);

        return http.build();
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

    @Bean
    public AdOAuth2AuthenticationProvider adOAuth2AuthenticationProvider(
            com.euripedes.authservice.provider.AdProvider adProvider) {
        return new AdOAuth2AuthenticationProvider(adProvider);
    }

    @Bean
    public RegisteredClientRepository registeredClientRepository(OAuth2ServerProperties properties) {
        String secret = properties.getClientSecret() == null ? "" : properties.getClientSecret().trim();

        RegisteredClient grafana = RegisteredClient.withId(UUID.randomUUID().toString())
                .clientId(properties.getClientId())
                .clientSecret(PasswordEncoderFactories.createDelegatingPasswordEncoder().encode(secret))
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
    public JWKSource<SecurityContext> jwkSource(OAuth2ServerProperties properties) {
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
            }
        };
    }

    /** Repassa as claims do id_token, grupos inclusive, no userinfo. */
    private static OidcUserInfo userInfo(OidcUserInfoAuthenticationContext context) {
        Map<String, Object> claims = new LinkedHashMap<>();
        Authentication authentication = context.getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof OidcUser user) {
            claims.put("sub", user.getSubject());
            String preferredUsername = user.getClaimAsString("preferred_username");
            claims.put("preferred_username", preferredUsername == null ? user.getSubject() : preferredUsername);
            String email = user.getEmail();
            claims.put("email", email == null ? user.getSubject() : email);
            String name = user.getClaimAsString("name");
            if (name != null) {
                claims.put("name", name);
            }
            List<String> groups = user.getClaimAsStringList("groups");
            claims.put("groups", groups == null ? List.of() : groups);
            String provider = user.getClaimAsString("provider");
            if (provider != null) {
                claims.put("provider", provider);
            }
        }
        return new OidcUserInfo(claims);
    }

    private RSAKey loadOrCreateKey(String directory) {
        Path dir = Path.of(directory);
        Path privateFile = dir.resolve("oauth-jwks-private.pem");
        Path publicFile = dir.resolve("oauth-jwks-public.pem");
        try {
            if (Files.exists(privateFile) && Files.exists(publicFile)) {
                RSAPrivateKey privateKey = (RSAPrivateKey) readPrivateKey(Files.readString(privateFile));
                RSAPublicKey publicKey = (RSAPublicKey) readPublicKey(Files.readString(publicFile));
                return new RSAKey.Builder(publicKey).privateKey(privateKey).build();
            }
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048, SecureRandom.getInstanceStrong());
            KeyPair pair = generator.generateKeyPair();
            Files.createDirectories(dir);
            Files.writeString(privateFile,
                    toPem("PRIVATE KEY", pair.getPrivate().getEncoded()));
            Files.writeString(publicFile,
                    toPem("PUBLIC KEY", pair.getPublic().getEncoded()));
            return new RSAKey.Builder((RSAPublicKey) pair.getPublic())
                    .privateKey((RSAPrivateKey) pair.getPrivate())
                    .algorithm(JWSAlgorithm.RS256)
                    .build();
        } catch (IOException e) {
            throw new UncheckedIOException("Falha ao preparar a chave do issuer em " + directory, e);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Nao foi possivel ler ou gerar a chave RSA do issuer", e);
        }
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
}