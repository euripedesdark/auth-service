package com.euripedes.authservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "auth.ldap")
public class LdapProperties {
    private String url;
    private String base;
    private String userDn;
    private String password;
    private String userSearchBase;
    public String getUrl(){return url;} public void setUrl(String v){url=v;}
    public String getBase(){return base;} public void setBase(String v){base=v;}
    public String getUserDn(){return userDn;} public void setUserDn(String v){userDn=v;}
    public String getPassword(){return password;} public void setPassword(String v){password=v;}
    public String getUserSearchBase(){return userSearchBase;} public void setUserSearchBase(String v){userSearchBase=v;}
}
