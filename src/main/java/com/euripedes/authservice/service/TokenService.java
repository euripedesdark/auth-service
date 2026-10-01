package com.euripedes.authservice.service;

import com.euripedes.authservice.config.TokenProperties;
import com.euripedes.authservice.contract.IdentityDto;
import com.euripedes.authservice.contract.TokenResponseDto;
import org.springframework.security.oauth2.jose.jws.JwsHeader;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import java.time.Instant;

@Service
public class TokenService {
    private final JwtEncoder encoder;
    private final TokenProperties properties;
    public TokenService(JwtEncoder encoder,TokenProperties properties){this.encoder=encoder;this.properties=properties;}
    public TokenResponseDto issue(IdentityDto identity){
        Instant now=Instant.now();
        Instant exp=now.plusSeconds(properties.getTtlSeconds());
        JwtClaimsSet claims=JwtClaimsSet.builder().issuer(properties.getIssuer()).issuedAt(now).expiresAt(exp)
            .subject(identity.identityId()).claim("username",identity.username()).claim("provider",identity.provider())
            .claim("groups",identity.groups()).build();
        String token=encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).type("JWT").build(),claims)).getTokenValue();
        return new TokenResponseDto(token,"Bearer",properties.getTtlSeconds(),identity);
    }
}