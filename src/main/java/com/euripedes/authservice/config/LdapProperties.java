package com.euripedes.authservice.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix="auth.ldap")
@Validated
public class LdapProperties {
    @NotBlank private String url;
    @NotBlank private String base;
    @NotBlank private String userDn;
    @NotBlank private String password;
    @NotBlank private String userSearchBase;
    private boolean requireSecure=true;
    @Positive private int connectTimeoutMs=5000;
    @Positive private int readTimeoutMs=5000;
    public String getUrl(){return url;} public void setUrl(String v){url=v;}
    public String getBase(){return base;} public void setBase(String v){base=v;}
    public String getUserDn(){return userDn;} public void setUserDn(String v){userDn=v;}
    public String getPassword(){return password;} public void setPassword(String v){password=v;}
    public String getUserSearchBase(){return userSearchBase;} public void setUserSearchBase(String v){userSearchBase=v;}
    public boolean isRequireSecure(){return requireSecure;} public void setRequireSecure(boolean v){requireSecure=v;}
    public int getConnectTimeoutMs(){return connectTimeoutMs;} public void setConnectTimeoutMs(int v){connectTimeoutMs=v;}
    public int getReadTimeoutMs(){return readTimeoutMs;} public void setReadTimeoutMs(int v){readTimeoutMs=v;}
}
