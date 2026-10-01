package com.euripedes.authservice.config;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import com.nimbusds.jose.jwk.source.ImmutableSecret;

@Configuration
@EnableConfigurationProperties(TokenProperties.class)
public class TokenConfig {
    @Bean SecretKey jwtSecretKey(TokenProperties p){
        byte[] key=p.getSecret().getBytes(java.nio.charset.StandardCharsets.UTF_8);
        if(key.length<32) throw new IllegalStateException("AUTH_TOKEN_SECRET must contain at least 32 bytes");
        return new SecretKeySpec(key,"HmacSHA256");
    }
    @Bean JwtEncoder jwtEncoder(SecretKey key){return new NimbusJwtEncoder(new ImmutableSecret<>(key));}
    @Bean JwtDecoder jwtDecoder(SecretKey key,TokenProperties p){
        NimbusJwtDecoder d=NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
        d.setJwtValidator(org.springframework.security.oauth2.jwt.JwtValidators.createDefaultWithIssuer(p.getIssuer()));
        return d;
    }
}