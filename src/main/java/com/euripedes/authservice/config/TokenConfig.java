package com.euripedes.authservice.config;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.OctetSequenceKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;

@Configuration
@EnableConfigurationProperties(TokenProperties.class)
public class TokenConfig {

    @Bean SecretKey jwtSecretKey(TokenProperties p){
        byte[] key=p.getSecret().getBytes(java.nio.charset.StandardCharsets.UTF_8);
        // 256 bits e' o minimo do HS256; abaixo disso o driver recusa o segredo.
        if(key.length<32) throw new IllegalStateException("AUTH_TOKEN_SECRET must contain at least 32 bytes");
        return new SecretKeySpec(key,"HmacSHA256");
    }

    /**
     * Encoder do token.
     *
     * A chave entra como OctetSequenceKey com algoritmo e uso declarados, via
     * ImmutableJWKSet. Nao da para usar ImmutableSecret: ele publica a chave
     * sem o campo alg, e o JWKMatcher que o NimbusJwtEncoder monta a partir do
     * header do token nao encontra nenhuma e o encode falha.
     *
     * O TokenService tambem precisa declarar HS256 no header -- o default do
     * Spring e' RS256, e uma chave HMAC nao satisfaz um matcher de RS256.
     *
     * <p><b>Nao e' bean de proposito.</b> O authorization server procura um
     * {@code JwtEncoder} no contexto e usa o primeiro que achar. Se este
     * encoder HMAC estivesse registrado, ele assinaria tambem os tokens do
     * OAuth com um header RS256, o seletor nao acharia chave nenhuma e a
     * troca de code por token devolveria 401 com "Failed to select a JWK
     * signing key". Sem bean, o authorization server monta o encoder dele a
     * partir do JWKSource RSA. O TokenService recebe a chave e monta o seu.
     */
    @Bean ApiTokenEncoder apiTokenEncoder(SecretKey key){
        return new ApiTokenEncoder(key);
    }

    /** Atalho usado pelos testes. */
    public JwtEncoder jwtEncoder(SecretKey key){
        OctetSequenceKey jwk=new OctetSequenceKey.Builder(key.getEncoded())
                .algorithm(JWSAlgorithm.HS256)
                .keyUse(KeyUse.SIGNATURE)
                .build();
        return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(jwk)));
    }

    /**
     * Decoder do token, para as rotas /me e /groups.
     *
     * O issuer e' validado como texto, e nao com o
     * JwtValidators.createDefaultWithIssuer. Aquele converte a claim iss em
     * java.net.URL, e o issuer deste servico ("auth-service") nao e' uma URL:
     * a conversao estourava IllegalArgumentException e nenhuma rota autenticada
     *respondia. Validar a claim direto mantem o mesmo valor de
     * AUTH_TOKEN_ISSUER, sem obrigar ninguem a reconfigurar.
     *
     * O restante da validacao (exp, nbf) e' o do JwtValidators.createDefault.
     */
    /**
     * Decoder do token interno. Fora do contexto pelo mesmo motivo do encoder:
     * o authorization server procura um JwtDecoder no contexto para validar o
     * Bearer do /userinfo. Com o decoder HS256 registrado, o token RSA do
     * issuer era rejeitado e o /userinfo caia no login.
     */
    public JwtDecoder jwtDecoder(SecretKey key,TokenProperties p){
        NimbusJwtDecoder d=NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();

        String esperado=p.getIssuer();
        OAuth2TokenValidator<Jwt> issuer=new JwtClaimValidator<>("iss",valor->esperado.equals(valor));

        d.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefault(),issuer));
        return d;
    }
}