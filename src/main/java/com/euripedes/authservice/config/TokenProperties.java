package com.euripedes.authservice.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix="auth.token")
@Validated
public class TokenProperties {
    @NotBlank private String secret;
    @NotBlank private String issuer="auth-service";
    @Positive private long ttlSeconds=900;
    public String getSecret(){return secret;} public void setSecret(String v){secret=v;}
    public String getIssuer(){return issuer;} public void setIssuer(String v){issuer=v;}
    public long getTtlSeconds(){return ttlSeconds;} public void setTtlSeconds(long v){ttlSeconds=v;}
}