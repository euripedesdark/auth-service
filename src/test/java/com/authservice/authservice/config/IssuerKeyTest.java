package com.authservice.authservice.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.nimbusds.jose.Algorithm;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWKMatcher;
import com.nimbusds.jose.jwk.JWKSelector;
import com.nimbusds.jose.jwk.KeyType;
import com.nimbusds.jose.jwk.KeyUse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/**
 * A chave do issuer precisa servir para ASSINAR, nao so para ser publicada.
 *
 * <p>Publicar o JWKS funciona mesmo com a chave errada — e' por isso que o
 * defeito so aparecia na troca de code por token, com "Failed to select a
 * JWK signing key". O encoder do Spring escolhe a chave por tipo, uso,
 * algoritmo e chave privada; este teste cobre exatamente essa busca.
 */
class IssuerKeyTest {

    private OAuth2ServerProperties properties(Path directory) {
        OAuth2ServerProperties properties = new OAuth2ServerProperties();
        properties.setJwkDirectory(directory.toString());
        return properties;
    }

    private JWKSelector anyRsa() {
        return new JWKSelector(new JWKMatcher.Builder().keyType(KeyType.RSA).build());
    }

    @Test
    void aChavePublicadaNoJwksTambémAssina(@TempDir Path directory) throws Exception {
        NimbusJwtEncoder encoder = new NimbusJwtEncoder(
                new AuthorizationServerConfig().jwkSource(properties(directory)));

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("http://localhost:8181")
                .subject("emilly")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(900))
                .claim("groups", List.of("GRP_DIRETORIA"))
                .build();

        String token = encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(SignatureAlgorithm.RS256).build(), claims)).getTokenValue();

        assertThat(token.split("\\.")).hasSize(3);
    }

    @Test
    void aChaveTemIdentificadorAlgoritmoEUsoDeAssinatura(@TempDir Path directory) throws Exception {
        var chaves = new AuthorizationServerConfig().jwkSource(properties(directory)).get(anyRsa(), null);

        assertThat(chaves).isNotEmpty();
        assertThat(chaves.get(0).getKeyID()).isNotBlank();
        assertThat(chaves.get(0).getKeyUse()).isEqualTo(KeyUse.SIGNATURE);
        assertThat(chaves.get(0).getAlgorithm()).isEqualTo(JWSAlgorithm.RS256);
        assertThat(chaves.get(0).isPrivate()).isTrue();
    }

    @Test
    void aChaveEmDiscoEReaproveitada(@TempDir Path directory) throws Exception {
        AuthorizationServerConfig config = new AuthorizationServerConfig();
        config.jwkSource(properties(directory));

        assertThat(Files.exists(directory.resolve("oauth-jwks-private.pem"))).isTrue();
        assertThat(Files.exists(directory.resolve("oauth-jwks-public.pem"))).isTrue();

        // Um novo start precisa assinar com a mesma chave, senao toda sessao
        // emitida antes do reinicio deixa de valer.
        String antes = config.jwkSource(properties(directory)).get(anyRsa(), null).get(0).getKeyID();
        String depois = config.jwkSource(properties(directory)).get(anyRsa(), null).get(0).getKeyID();

        assertThat(depois).isEqualTo(antes);
    }

    /**
     * Reproduz o seletor que o NimbusJwtEncoder monta para ASSINAR.
     *
     * <p>E' o caminho que falhava em producao: o encoder monta o matcher com
     * keyType, keyID do cabecalho, privateOnly e algoritmo. Se esse matcher
     * volta vazio, o encoder estoura "Failed to select a JWK signing key" e
     * a troca de code por token devolve 401 sem explicar por que.
     */
    @Test
    void oSeletorDeAssinaturaDoEncoderEncontraAChave(@TempDir Path directory) throws Exception {
        var jwkSource = new AuthorizationServerConfig().jwkSource(properties(directory));

        var comKid = new JWKMatcher.Builder()
                .keyType(KeyType.RSA)
                .keyID(jwkSource.get(anyRsa(), null).get(0).getKeyID())
                .privateOnly(true)
                .algorithms(new Algorithm[]{JWSAlgorithm.RS256})
                .build();
        assertThat(jwkSource.get(new JWKSelector(comKid), null))
                .as("seletor com o kid da propria chave")
                .isNotEmpty();

        var semKid = new JWKMatcher.Builder()
                .keyType(KeyType.RSA)
                .privateOnly(true)
                .algorithms(new Algorithm[]{JWSAlgorithm.RS256})
                .build();
        assertThat(jwkSource.get(new JWKSelector(semKid), null))
                .as("seletor sem kid, como o header sem kid")
                .isNotEmpty();
    }
}