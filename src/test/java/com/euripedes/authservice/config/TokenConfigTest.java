package com.euripedes.authservice.config;

import com.euripedes.authservice.contract.IdentityDto;
import com.euripedes.authservice.contract.TokenResponseDto;
import com.euripedes.authservice.service.TokenService;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regressao do token.
 *
 * Este teste existe porque o /token ja falhou silenciosamente, e o sintoma foi
 * enganoso: o NimbusJwtEncoder nao achava chave de assinatura e devolvia 401,
 * exatamente como uma senha errada. A causa eram duas coisas somadas -- a
 * chave HMAC era publicada sem algoritmo, e o TokenService nao declarava
 * HS256 no header (o default do Spring e' RS256). Qualquer coisa que emita
 * token precisa passar por aqui.
 *
 * O teste fica no pacote config porque os metodos @Bean do TokenConfig sao
 * package-private, como o resto do projeto.
 */
class TokenConfigTest {

    /** 64 bytes; o minimo do HS256 e' 32. */
    private static final String SEGREDO = "a".repeat(64);

    private TokenProperties props(){
        TokenProperties p = new TokenProperties();
        p.setSecret(SEGREDO);
        p.setIssuer("auth-service");
        p.setTtlSeconds(900);
        return p;
    }

    private TokenService service(){
        TokenConfig config = new TokenConfig();
        SecretKey chave = config.jwtSecretKey(props());
        return new TokenService(config.jwtEncoder(chave), props());
    }

    @Test void emiteTokenQueVoltaAssinadoEmHS256(){
        TokenResponseDto resposta = service().issue(
                new IdentityDto("sa","sa","POSTGRES",List.of("BRASIL_SAAS_TODOS")));

        assertNotNull(resposta.accessToken(),"o token nao pode vir nulo");
        assertEquals(3, resposta.accessToken().split("\\.").length,
                "o token precisa ter header, payload e assinatura");

        TokenConfig config = new TokenConfig();
        SecretKey chave = config.jwtSecretKey(props());
        Jwt jwt = config.jwtDecoder(chave,props()).decode(resposta.accessToken());

        assertEquals("HS256", jwt.getHeaders().get("alg"));
        // A claim iss e' comparada como texto: o issuer deste servico nao e'
        // uma URL, e Jwt.getIssuer() quebraria tentando converter.
        assertEquals("auth-service", jwt.getClaimAsString("iss"));
        assertEquals("sa", jwt.getSubject());
        assertEquals("sa", jwt.getClaimAsString("username"));
        assertEquals("POSTGRES", jwt.getClaimAsString("provider"));
        assertEquals(List.of("BRASIL_SAAS_TODOS"), jwt.getClaimAsStringList("groups"));
        assertEquals(900, resposta.expiresIn());
        assertEquals("Bearer", resposta.tokenType());
    }

    @Test void oLoginPorCertificadoTambemRecebeOGrupoDeAcessoTotal(){
        TokenResponseDto resposta = service().issue(
                new IdentityDto("joao","joao","CERTIFICADO",List.of("BRASIL_SAAS_TODOS")));

        TokenConfig config = new TokenConfig();
        SecretKey chave = config.jwtSecretKey(props());
        Jwt jwt = config.jwtDecoder(chave,props()).decode(resposta.accessToken());

        assertEquals("CERTIFICADO", jwt.getClaimAsString("provider"));
        assertEquals(List.of("BRASIL_SAAS_TODOS"), jwt.getClaimAsStringList("groups"));
    }

    @Test void umTokenEmitidoComUmaChaveNaoPassaEmOutra(){
        TokenConfig config = new TokenConfig();
        TokenResponseDto emitido = service().issue(
                new IdentityDto("sa","sa","POSTGRES",List.of("BRASIL_SAAS_TODOS")));

        TokenProperties outra = props();
        outra.setSecret("b".repeat(64));
        JwtDecoder decoder = config.jwtDecoder(config.jwtSecretKey(outra),props());

        assertThrows(Exception.class,()->decoder.decode(emitido.accessToken()),
                "um token assinado com outro segredo tem de ser recusado");
    }

    @Test void umIssuerQueNaoEUrlAindaValida(){
        // Regressao: JwtValidators.createDefaultWithIssuer converte a claim
        // iss em URL e estoura com o issuer "auth-service". Aqui o mesmo issuer
        // passa, porque a claim e' comparada como texto.
        TokenConfig config = new TokenConfig();
        SecretKey chave = config.jwtSecretKey(props());
        TokenResponseDto emitido = service().issue(
                new IdentityDto("sa","sa","POSTGRES",List.of("BRASIL_SAAS_TODOS")));

        Jwt jwt = config.jwtDecoder(chave,props()).decode(emitido.accessToken());
        assertEquals("auth-service", jwt.getClaimAsString("iss"));
    }

    @Test void recusaTokenDeOutroIssuer(){
        TokenConfig config = new TokenConfig();

        TokenProperties emitente = props();
        emitente.setIssuer("outro-servico");
        TokenResponseDto emitido = new TokenService(
                config.jwtEncoder(config.jwtSecretKey(emitente)), emitente)
                .issue(new IdentityDto("sa","sa","POSTGRES",List.of("BRASIL_SAAS_TODOS")));

        JwtDecoder decoder = config.jwtDecoder(config.jwtSecretKey(props()),props());
        assertThrows(Exception.class,()->decoder.decode(emitido.accessToken()),
                "token de outro issuer tem de ser recusado");
    }

    @Test void recusaTokenExpirado(){
        // O JwtClaimsSet nao deixa emitir exp antes de iat, entao o token
        // vencido e' montado direto, do mesmo jeito que um token de outro
        // sistema seria.
        TokenConfig config = new TokenConfig();
        SecretKey chave = config.jwtSecretKey(props());

        Instant agora=Instant.now();
        JwtClaimsSet vencido=JwtClaimsSet.builder()
                .issuer(props().getIssuer())
                .issuedAt(agora.minusSeconds(3600))
                .expiresAt(agora.minusSeconds(60))
                .subject("sa")
                .claim("username","sa")
                .build();
        String token=config.jwtEncoder(chave).encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(),vencido)).getTokenValue();

        JwtDecoder decoder = config.jwtDecoder(chave,props());
        assertThrows(Exception.class,()->decoder.decode(token),
                "token expirado tem de ser recusado");
    }

    @Test void recusaTokenAlteradoNoPayload(){
        TokenConfig config = new TokenConfig();
        SecretKey chave = config.jwtSecretKey(props());
        TokenResponseDto emitido = service().issue(
                new IdentityDto("sa","sa","POSTGRES",List.of("BRASIL_SAAS_TODOS")));

        // troca "groups" por um valor maior que o original: o payload muda e a
        // assinatura deixa de valer.
        String[] partes=emitido.accessToken().split("\\.");
        String alterado=partes[0]+"."+Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"iss\":\"auth-service\",\"sub\":\"sa\"}".getBytes())+"."+partes[2];

        JwtDecoder decoder = config.jwtDecoder(chave,props());
        assertThrows(Exception.class,()->decoder.decode(alterado),
                "token adulterado tem de ser recusado");
    }

    @Test void recusaSegredoCurtoDemaisParaHs256(){
        TokenProperties curta = props();
        curta.setSecret("a".repeat(31));
        assertThrows(IllegalStateException.class,()->new TokenConfig().jwtSecretKey(curta));
    }

    @Test void aceitaSegredoDeTrintaEDoisBytes(){
        TokenProperties noLimite = props();
        noLimite.setSecret("a".repeat(32));
        assertTrue(noLimite.getSecret().length() == 32);
        assertNotNull(new TokenConfig().jwtSecretKey(noLimite));
    }
}