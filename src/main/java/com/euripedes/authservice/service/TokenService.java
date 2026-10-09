package com.euripedes.authservice.service;

import com.euripedes.authservice.config.TokenProperties;
import com.euripedes.authservice.contract.IdentityDto;
import com.euripedes.authservice.contract.TokenResponseDto;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.util.Objects;

@Service
public class TokenService {
    private final JwtEncoder encoder;
    private final TokenProperties properties;
    public TokenService(JwtEncoder encoder,TokenProperties properties){this.encoder=encoder;this.properties=properties;}
    public TokenResponseDto issue(IdentityDto identity){
        Objects.requireNonNull(identity, "identity");
        if (identity.provider() == null || identity.provider().isBlank()
                || identity.identityId() == null || identity.identityId().isBlank()
                || identity.username() == null || identity.username().isBlank()
                || identity.groups() == null) {
            throw new IllegalArgumentException("Canonical identity requires provider, identityId, username and groups");
        }
        Instant now=Instant.now();
        Instant exp=now.plusSeconds(properties.getTtlSeconds());
        JwtClaimsSet claims=JwtClaimsSet.builder().issuer(properties.getIssuer()).issuedAt(now).expiresAt(exp)
              .subject(identity.identityId()).claim("identityId",identity.identityId())
              .claim("username",identity.username()).claim("provider",identity.provider())
              .claim("groups",identity.groups()).build();

        // O header precisa declarar HS256 explicitamente.
        //
        // JwtEncoderParameters.from(claims) deixa o header nulo, e o
        // NimbusJwtEncoder usa RS256 como default. Como a chave aqui e' HMAC, o
        // JWKMatcher procurava uma chave RS256, nao achava nenhuma e o encode
        // falhava com "Failed to select a JWK signing key" -- o /token devolvia
        // 401 para todos os provedores.
        JwsHeader header=JwsHeader.with(MacAlgorithm.HS256).build();

        String token=encoder.encode(JwtEncoderParameters.from(header,claims)).getTokenValue();
        return new TokenResponseDto(token,"Bearer",properties.getTtlSeconds(),identity);
    }
}