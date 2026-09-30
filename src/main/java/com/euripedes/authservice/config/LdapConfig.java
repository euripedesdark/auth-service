package com.euripedes.authservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.ldap.core.LdapTemplate;
import org.springframework.ldap.core.support.LdapContextSource;

import java.util.Hashtable;

@Configuration
public class LdapConfig {

    @Bean
    public LdapTemplate ldapTemplate() {
        LdapContextSource source = new LdapContextSource();
        source.setUrl("ldap://127.0.0.1:389");
        source.setBase("DC=srvcloud,DC=cloud");
        source.setUserDn("cn=admin,DC=srvcloud,DC=cloud");
        source.setPassword("admin123");
        source.afterPropertiesSet();

        return new LdapTemplate(source);
    }
