package com.euripedes.authservice.config;

import javax.crypto.SecretKey;
import com.nimbusds.jose.JWSAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.OctetSequenceKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;

/**
 * Encoder do token interno do auth-service (HS256).
 *
 * <p>Existe como tipo proprio, e nao como {@link JwtEncoder} no contexto,
 * por causa do authorization server. Ele procura um {@code JwtEncoder} no
 * contexto e usa o primeiro que achar: se o encoder HMAC estivesse
 * registrado, ele assinaria tambem os tokens do OAuth — que vao com header
 * RS256 — o seletor nao encontraria chave RSA nenhuma e a troca de code por
 * token devolveria 401 com "Failed to select a JWK signing key".
 */
public class ApiTokenEncoder {

    private final JwtEncoder encoder;

    public ApiTokenEncoder(SecretKey key) {
        OctetSequenceKey jwk = new OctetSequenceKey.Builder(key.getEncoded())
                .algorithm(JWSAlgorithm.HS256)
                .keyUse(KeyUse.SIGNATURE)
                .build();
        this.encoder = new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(jwk)));
    }

    /** Assina as claims com o header informado. */
    public String encode(JwsHeader header, JwtClaimsSet claims) {
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}